package cn.openlims.platform.task;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 请求独立提交，验证失败回滚和并发锁；H2 不能替代上线前的 InnoDB 验收。
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:task-supplement;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskSupplementApiTests {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mvc;
    private String teacher;
    private String memberId;
    private String coreId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.execute("ALTER TABLE task_assignments ADD CONSTRAINT IF NOT EXISTS "
                + "uk_task_supplement_member UNIQUE (task_id, member_profile_id)");
        teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String options = mvc.perform(get("/api/v1/admin/tasks/member-options").header("Authorization", teacher))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        memberId = ((List<String>) JsonPath.read(options, "$.data[?(@.role == 'MEMBER')].profileId")).getFirst();
        coreId = ((List<String>) JsonPath.read(options, "$.data[?(@.role == 'CORE_STUDENT')].profileId")).getFirst();
    }

    @Test
    void explicitSupplementPreservesSubmittedObjectAndNotifiesOnlyNewRecipients() throws Exception {
        String task = publishedTask();
        String member = login("member", "OpenLIMS-Member-2026!");
        String assignment = JsonPath.read(progress(task), "$.data.assignments[0].assignmentId");
        mvc.perform(post("/api/v1/tasks/{id}/submission", assignment).header("Authorization", member)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completionNote\":\"已完成，保留提交。\"}"))
                .andExpect(status().isOk());
        Object before = JsonPath.read(progress(task), "$.data.assignments[0]");
        supplement(task, ids(memberId, coreId, coreId), 1, 1);
        String after = progress(task);
        assertEquals(before, ((List<?>) JsonPath.read(after, "$.data.assignments[?(@.memberProfileId == '" + memberId + "')]")).getFirst());
        assertEquals("MANUAL", ((List<String>) JsonPath.read(after, "$.data.assignments[?(@.memberProfileId == '" + coreId + "')].source")).getFirst());
        assertEquals(2, notificationCount(task));
        supplement(task, ids(memberId, coreId), 0, 2);
        assertEquals(2, notificationCount(task));
        mvc.perform(put("/api/v1/admin/tasks/{task}/assignments/{id}/review", task, assignment)
                        .header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"comment\":\"通过\"}"))
                .andExpect(status().isOk());
        Object approved = JsonPath.read(progress(task), "$.data.assignments[?(@.memberProfileId == '" + memberId + "')]");
        supplement(task, ids(memberId), 0, 1);
        assertEquals(approved, JsonPath.read(progress(task), "$.data.assignments[?(@.memberProfileId == '" + memberId + "')]") );
        assertEquals(2, notificationCount(task));
    }

    @Test
    void invalidInputRollsBackWholeSupplementAndValidatesLimit() throws Exception {
        String task = publishedTask();
        for (String payload : List.of(ids(coreId, UUID.randomUUID().toString()), "{\"rules\":[],\"memberProfileIds\":[]}",
                "{\"memberProfileIds\":[null]}", ids(IntStream.range(0, 101).mapToObj(i -> coreId).toArray(String[]::new)))) {
            mvc.perform(post("/api/v1/admin/tasks/{id}/assignments", task).header("Authorization", teacher)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
            assertEquals(1, (int) JsonPath.read(progress(task), "$.data.assignmentCount"));
            assertEquals(1, notificationCount(task));
        }
    }

    @Test
    void visitorProfileIsRejectedEvenWhenSpecifiedDirectly() throws Exception {
        String task = publishedTask();
        jdbc.update("UPDATE accounts SET role = 'VISITOR' WHERE id = (SELECT account_id FROM member_profiles WHERE id = ?)", UUID.fromString(coreId));
        try {
            mvc.perform(post("/api/v1/admin/tasks/{id}/assignments", task).header("Authorization", teacher)
                            .contentType(MediaType.APPLICATION_JSON).content(ids(coreId)))
                    .andExpect(status().isBadRequest());
            assertEquals(1, notificationCount(task));
        } finally {
            jdbc.update("UPDATE accounts SET role = 'CORE_STUDENT' WHERE id = (SELECT account_id FROM member_profiles WHERE id = ?)", UUID.fromString(coreId));
        }
    }

    @Test
    void legacyCriteriaRemainCompatibleAndExplicitSourceTakesPriority() throws Exception {
        String task = publishedTask();
        supplement(task, "{\"rules\":[{\"dimension\":\"ROLE\",\"value\":\"CORE_STUDENT\"}],\"memberProfileIds\":[\"" + coreId + "\"]}", 1, 0);
        assertEquals("MANUAL", ((List<String>) JsonPath.read(progress(task), "$.data.assignments[?(@.memberProfileId == '" + coreId + "')].source")).getFirst());
        supplement(task, "{\"rules\":[{\"dimension\":\"ROLE\",\"value\":\"TEACHER\"}]}", 1, 0);
        assertEquals("CRITERIA", ((List<String>) JsonPath.read(progress(task), "$.data.assignments[?(@.role == 'TEACHER')].source")).getFirst());
        assertEquals(3, notificationCount(task));
    }

    @Test
    void authorizationAndTaskStateAreEnforced() throws Exception {
        String task = publishedTask();
        mvc.perform(post("/api/v1/admin/tasks/{id}/assignments", task).contentType(MediaType.APPLICATION_JSON).content(ids(coreId)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/tasks/{id}/assignments", task).header("Authorization", login("member", "OpenLIMS-Member-2026!"))
                        .contentType(MediaType.APPLICATION_JSON).content(ids(coreId)))
                .andExpect(status().isForbidden());
        String draft = createTask();
        mvc.perform(post("/api/v1/admin/tasks/{id}/assignments", draft).header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON).content(ids(coreId)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/tasks/{id}/close", task).header("Authorization", teacher)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/tasks/{id}/assignments", task).header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON).content(ids(coreId)))
                .andExpect(status().isConflict());
        // 只读新手入口可能返回未落库的默认任务；使用专用已持久化对象检查类型守卫。
        jdbc.update("UPDATE tasks SET task_type = 'ONBOARDING' WHERE id = ?", UUID.fromString(draft));
        mvc.perform(post("/api/v1/admin/tasks/{id}/assignments", draft).header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON).content(ids(coreId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentSupplementsCreateOneAssignmentAndOneNotification() throws Exception {
        String task = publishedTask();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return supplementResponse(task, ids(coreId)); });
            var second = executor.submit(() -> { start.await(); return supplementResponse(task, ids(coreId)); });
            start.countDown();
            String a = first.get(15, TimeUnit.SECONDS);
            String b = second.get(15, TimeUnit.SECONDS);
            assertEquals(1, (int) JsonPath.read(a, "$.data.supplementResult.createdCount") + (int) JsonPath.read(b, "$.data.supplementResult.createdCount"));
            assertEquals(1, (int) JsonPath.read(a, "$.data.supplementResult.skippedExistingCount") + (int) JsonPath.read(b, "$.data.supplementResult.skippedExistingCount"));
        }
        assertEquals(2, (int) JsonPath.read(progress(task), "$.data.assignmentCount"));
        assertEquals(2, notificationCount(task));
    }

    private String createTask() throws Exception {
        String response = mvc.perform(post("/api/v1/admin/tasks").header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"按人补发验收\",\"contentHtml\":\"<p>完成并提交。</p>\",\"points\":10,\"memberProfileIds\":[\"" + memberId + "\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }
    private String publishedTask() throws Exception {
        String id = createTask();
        mvc.perform(post("/api/v1/admin/tasks/{id}/publish", id).header("Authorization", teacher)).andExpect(status().isOk());
        return id;
    }
    private String ids(String... ids) {
        return "{\"rules\":[],\"memberProfileIds\":[" + List.of(ids).stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",")) + "]}";
    }
    private String supplementResponse(String task, String payload) throws Exception {
        return mvc.perform(post("/api/v1/admin/tasks/{id}/assignments", task).header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    private void supplement(String task, String payload, int created, int skipped) throws Exception {
        String response = supplementResponse(task, payload);
        assertEquals(created, (int) JsonPath.read(response, "$.data.supplementResult.createdCount"));
        assertEquals(skipped, (int) JsonPath.read(response, "$.data.supplementResult.skippedExistingCount"));
    }
    private String progress(String task) throws Exception {
        return mvc.perform(get("/api/v1/admin/tasks/{id}/progress", task).header("Authorization", teacher))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    private int notificationCount(String task) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications n JOIN task_assignments a ON n.target_path = CONCAT('/tasks/', CAST(a.id AS VARCHAR)) WHERE a.task_id = ? AND n.type = 'TASK_ASSIGNED'", Integer.class, UUID.fromString(task));
    }
    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.data.accessToken");
    }
}

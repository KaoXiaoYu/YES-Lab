package cn.yeslab.platform.administration;

import com.jayway.jsonpath.JsonPath;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.task.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class ExperienceRefreshApiTests {

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager entityManager;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void discussionPagesSplitTheBoardAndSearchAcrossEveryPost() throws Exception {
        String coreToken = login("core", "YesLab-Core-2026!");
        String marker = "page-" + UUID.randomUUID().toString().substring(0, 8);
        for (int index = 0; index < 3; index++) {
            mvc.perform(post("/api/v1/discussions").header("Authorization", bearer(coreToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"" + marker + " 第" + index + "帖\",\"content\":\"<p>分页测试 " + index + "</p>\"}"))
                    .andExpect(status().isOk());
        }

        String first = mvc.perform(get("/api/v1/discussions/page").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.totalCount", greaterThanOrEqualTo(3)))
                .andReturn().getResponse().getContentAsString();
        List<String> firstIds = JsonPath.read(first, "$.data.items[*].id");
        assertThat(firstIds).hasSize(2);

        String second = mvc.perform(get("/api/v1/discussions/page").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> secondIds = JsonPath.read(second, "$.data.items[*].id");
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);

        mvc.perform(get("/api/v1/discussions/page").param("q", marker + " 第2帖"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value(marker + " 第2帖"))
                .andExpect(jsonPath("$.data.hasMore").value(false));

        // The original unpaged list keeps working for existing callers.
        mvc.perform(get("/api/v1/discussions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem(marker + " 第0帖")));
    }

    @Test
    void adminOverviewIsLimitedToAdministratorsAndListsEveryModule() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");

        mvc.perform(get("/api/v1/admin/overview").header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/overview")).andExpect(status().isUnauthorized());

        String body = mvc.perform(get("/api/v1/admin/overview").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(7))
                .andExpect(jsonPath("$.data.items[*].key", hasItem("TASK_REVIEW")))
                .andExpect(jsonPath("$.data.items[*].href", hasItem("/admin/recruitment")))
                .andReturn().getResponse().getContentAsString();
        List<Integer> counts = JsonPath.read(body, "$.data.items[*].count");
        int total = JsonPath.read(body, "$.data.totalPending");
        assertThat(counts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(total);
    }

    @Test
    void paginationClampsBoundsAndCannotOverflowOnHugePages() throws Exception {
        mvc.perform(get("/api/v1/discussions/page").param("page", "-1").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1));
        mvc.perform(get("/api/v1/discussions/page").param("page", "2147483647").param("size", "2147483647"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.hasMore").value(false));
        mvc.perform(get("/api/v1/discussions/page").param("sort", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pinnedPostsSearchAndViewerPermissionsKeepTheirExistingMeaning() throws Exception {
        String token = login("core", "YesLab-Core-2026!");
        String marker = "needle-" + UUID.randomUUID();
        String created = mvc.perform(post("/api/v1/discussions").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + marker + "\",\"content\":\"<p>MixedCASE body</p>\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.data.id");
        Number number = JsonPath.read(created, "$.data.contentNumber");
        mvc.perform(patch("/api/v1/discussions/" + id + "/pin").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/discussions/page").param("size", "1").param("page", "2147483647"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinned[*].id", hasItem(id)));
        mvc.perform(get("/api/v1/discussions/page").param("q", marker + " mixedcase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.pinned.length()").value(0))
                .andExpect(jsonPath("$.data.items[0].canEdit").value(false));
        mvc.perform(get("/api/v1/discussions/page").param("q", String.format("#D%06d", number.longValue()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(id))
                .andExpect(jsonPath("$.data.items[0].canEdit").value(true));
        mvc.perform(get("/api/v1/discussions/page").param("q", marker + " absentword"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    void overviewExcludesClosedTasksButKeepsOverdueReviewableTasks() throws Exception {
        String token = login("core", "YesLab-Core-2026!");
        AccountEntity actor = entityManager.createQuery("select a from AccountEntity a where a.username = 'core'", AccountEntity.class)
                .getSingleResult();
        MemberProfileEntity profile = entityManager.createQuery("select p from MemberProfileEntity p where p.account = :actor", MemberProfileEntity.class)
                .setParameter("actor", actor).getSingleResult();
        long before = taskReviewCount(token);
        for (TaskStatus taskStatus : List.of(TaskStatus.PUBLISHED, TaskStatus.CLOSED)) {
            TaskEntity task = new TaskEntity(TaskType.STANDARD, "overview test", "<p>test</p>",
                    LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), 0, taskStatus, actor);
            entityManager.persist(task);
            TaskAssignmentEntity assignment = new TaskAssignmentEntity(task, profile, null, TaskAssignmentSource.MANUAL);
            assignment.submit("Submitted before deadline");
            entityManager.persist(assignment);
        }
        entityManager.flush();
        assertThat(taskReviewCount(token)).isEqualTo(before + 1);
    }

    private long taskReviewCount(String token) throws Exception {
        String body = mvc.perform(get("/api/v1/admin/overview").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> counts = JsonPath.read(body, "$.data.items[?(@.key == 'TASK_REVIEW')].count");
        return counts.getFirst().longValue();
    }

    private String login(String username, String password) throws Exception {
        String content = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(content, "$.data.accessToken");
    }

    private static String bearer(String token) { return "Bearer " + token; }
}

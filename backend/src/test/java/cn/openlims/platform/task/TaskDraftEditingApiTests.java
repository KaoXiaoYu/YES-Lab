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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 独立数据库，HTTP 请求独立提交，避免测试事务回滚掩盖生产库的唯一约束冲突。
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:task-draft-edit;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskDraftEditingApiTests {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // V11 已有此约束；H2 测试关闭 Flyway，必须显式补上才能覆盖实际存储约束。
        jdbc.execute("ALTER TABLE task_audience_rules ADD CONSTRAINT IF NOT EXISTS "
                + "uk_task_audience_rules UNIQUE (task_id, dimension, rule_value)");
    }

    @Test
    void draftCanBeSavedRepeatedlyWithoutLosingRulesMembersOrSubtaskIds() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = login("member", "OpenLIMS-Member-2026!");
        String profile = mvc.perform(get("/api/v1/member/profile").header("Authorization", member))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String memberId = JsonPath.read(profile, "$.data.id");
        String created = mvc.perform(post("/api/v1/admin/tasks").header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON).content(payload(memberId, "null")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(created, "$.data.id");
        String subtaskId = JsonPath.read(created, "$.data.subtasks[0].id");

        for (int attempt = 0; attempt < 3; attempt++) {
            mvc.perform(put("/api/v1/admin/tasks/{id}", taskId).header("Authorization", teacher)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload(memberId, "\"" + subtaskId + "\"")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.rules.length()").value(2))
                    .andExpect(jsonPath("$.data.assignmentCount").value(1))
                    .andExpect(jsonPath("$.data.memberProfileIds[0]").value(memberId))
                    .andExpect(jsonPath("$.data.subtasks[0].id").value(subtaskId));
        }
        mvc.perform(get("/api/v1/admin/tasks/{id}", taskId).header("Authorization", teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberProfileIds[0]").value(memberId));
        // flush 不是提交：后续指定成员校验失败，旧规则、子任务与指定名单必须整体回滚。
        String invalid = payload("00000000-0000-0000-0000-000000000000", "\"" + subtaskId + "\"")
                .replace("\"value\":\"MEMBER\"", "\"value\":\"CORE_STUDENT\"");
        mvc.perform(put("/api/v1/admin/tasks/{id}", taskId).header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/tasks/{id}", taskId).header("Authorization", teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rules[?(@.dimension == 'ROLE')].value").value(org.hamcrest.Matchers.contains("MEMBER")))
                .andExpect(jsonPath("$.data.memberProfileIds[0]").value(memberId))
                .andExpect(jsonPath("$.data.subtasks[0].id").value(subtaskId));
        String changed = payload(memberId, "\"" + subtaskId + "\"")
                .replace("\"value\":\"MEMBER\"", "\"value\":\"CORE_STUDENT\"")
                .replace("\"memberProfileIds\":[\"" + memberId + "\"]", "\"memberProfileIds\":[]");
        mvc.perform(put("/api/v1/admin/tasks/{id}", taskId).header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberProfileIds.length()").value(0))
                .andExpect(jsonPath("$.data.assignmentCount").value(0));
    }

    private String payload(String memberId, String subtaskId) {
        return """
                {"title":"3D 建模","contentHtml":"<p>完成模型并提交文件。</p>","points":0,
                 "subtasks":[{"id":%s,"title":"制作模型","contentHtml":"<p>保留子任务说明。</p>"}],
                 "rules":[{"dimension":"ROLE","value":"MEMBER"},
                          {"dimension":"MEMBER_STATUS","value":"OFFICIAL"}],
                 "memberProfileIds":["%s"]}
                """.formatted(subtaskId, memberId);
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.data.accessToken");
    }
}

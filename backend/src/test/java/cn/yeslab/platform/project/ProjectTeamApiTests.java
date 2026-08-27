package cn.yeslab.platform.project;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ProjectTeamApiTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void systemAdminCanCreateProjectWithTeacherAdvisorAndOrdinaryLeaderCanManageIt() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String coreToken = login("core", "YesLab-Core-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");
        String options = mvc.perform(get("/api/v1/projects/member-options")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String teacherId = findProfileId(options, "汤洪大王", "TEACHER");
        String coreId = findProfileId(options, "范桌轩大王", "CORE_STUDENT");
        String memberId = findProfileId(options, "范桌轩大王", "MEMBER");
        String requestPayload = projectPayload(memberId, teacherId, true)
                .replace("\"memberProfileIds\":[\"" + memberId + "\"]",
                        "\"memberProfileIds\":[\"" + memberId + "\",\"" + coreId + "\"]")
                .replace("\"administratorProfileIds\":[]",
                        "\"administratorProfileIds\":[\"" + coreId + "\"]");

        mvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload))
                .andExpect(status().isForbidden());

        String created = mvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leader.id").value(memberId))
                .andExpect(jsonPath("$.data.leader.memberCode").value(notNullValue()))
                .andExpect(jsonPath("$.data.advisor.id").value(teacherId))
                .andExpect(jsonPath("$.data.members[*].id", hasItem(memberId)))
                .andReturn().getResponse().getContentAsString();
        String projectId = JsonPath.read(created, "$.data.id");

        mvc.perform(put("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectName":"普通成员负责的测试项目","description":"负责人可以维护项目资料。",
                                  "type":"INTERNAL","status":"ACTIVE","advisorProfileId":"%s",
                                  "requiredSkillTags":["工程实现"],"startDate":"2026-08-01","endDate":"2026-12-31",
                                  "stageGoals":["完成原型"],"progressDescription":"需求已确认","outcomes":null,
                                  "gitRepositoryUrl":"https://github.com/yes-lab/demo","documentUrl":null,"externallyVisible":true
                                }
                                """.formatted(teacherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectName").value("普通成员负责的测试项目"))
                .andExpect(jsonPath("$.data.canEditProject").value(true))
                .andExpect(jsonPath("$.data.canManageTeam").value(true));

        mvc.perform(put("/api/v1/projects/{id}/team", projectId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamName":"MEMBER-LED / 01","leaderProfileId":"%s",
                                 "memberProfileIds":["%s","%s"],"administratorProfileIds":["%s"]}
                                """.formatted(memberId, memberId, coreId, coreId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamName").value("MEMBER-LED / 01"));

        MockMultipartFile cover = new MockMultipartFile(
                "cover",
                "project-cover.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0}
        );
        mvc.perform(multipart("/api/v1/projects/{id}/cover", projectId)
                        .file(cover)
                        .header("Authorization", bearer(coreToken))
                        .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverImageOriginalName").value("project-cover.png"))
                .andExpect(jsonPath("$.data.coverImageUrl").value("/api/v1/projects/" + projectId + "/cover"));

        mvc.perform(get("/api/v1/projects/{id}/cover", projectId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/public/project-teams/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.advisor.name").value("汤洪大王"))
                .andExpect(jsonPath("$.data.leader.memberCode").value(nullValue()))
                .andExpect(jsonPath("$.data.advisor.memberCode").value(nullValue()))
                .andExpect(jsonPath("$.data.members[0].memberCode").value(nullValue()))
                .andExpect(jsonPath("$.data.coverImageUrl").value(startsWith("/api/v1/public/project-teams/" + projectId + "/cover?v=")))
                .andExpect(jsonPath("$.data.administrators").doesNotExist())
                .andExpect(jsonPath("$.data.canEditProject").doesNotExist());

        mvc.perform(get("/api/v1/public/project-teams/{id}/cover", projectId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")));
    }

    @Test
    void advisorMustBeTeacherAndHiddenProjectsStayPrivate() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String options = mvc.perform(get("/api/v1/projects/member-options")
                        .header("Authorization", bearer(teacherToken)))
                .andReturn().getResponse().getContentAsString();
        String memberId = findProfileId(options, "范桌轩大王", "MEMBER");

        mvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectPayload(memberId, memberId, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("项目指导老师必须选择教师账号"));

        String created = mvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectPayload(memberId, null, false)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String projectId = JsonPath.read(created, "$.data.id");

        mvc.perform(get("/api/v1/public/project-teams/{id}", projectId))
                .andExpect(status().isNotFound());
    }

    private String projectPayload(String leaderId, String advisorId, boolean externallyVisible) {
        String advisor = advisorId == null ? "null" : "\"" + advisorId + "\"";
        return """
                {
                  "projectName":"权限测试项目","teamName":"TEST / 01","description":"验证项目角色和指导老师关联。",
                  "type":"RESEARCH","status":"PLANNING","leaderProfileId":"%s","advisorProfileId":%s,
                  "memberProfileIds":["%s"],"administratorProfileIds":[],"requiredSkillTags":["机器人控制"],
                  "startDate":"2026-08-01","endDate":"2026-12-31","stageGoals":["完成验证"],
                  "progressDescription":null,"outcomes":null,"gitRepositoryUrl":null,"documentUrl":null,
                  "externallyVisible":%s
                }
                """.formatted(leaderId, advisor, leaderId, externallyVisible);
    }

    private String findProfileId(String response, String name, String role) {
        List<String> ids = JsonPath.read(response,
                "$.data[?(@.name == '" + name + "' && @.role == '" + role + "')].id");
        return ids.getFirst();
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

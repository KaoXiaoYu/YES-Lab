package cn.yeslab.platform.identity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.http.Cookie;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class IdentityRecruitmentApiTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void visitorCanApplyAndAdminCanConvertTheAccountToMember() throws Exception {
        String visitorToken = tokenFrom(mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"VisitorFlow@Example.com","password":"Visitor-Flow-2026!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.role").value("VISITOR"))
                .andExpect(jsonPath("$.data.account.username").value("visitorflow@example.com"))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(visitorToken)))
                .andExpect(status().isForbidden());

        String questionsResponse = mvc.perform(get("/api/v1/recruitment/me/questions").header("Authorization", bearer(visitorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andReturn().getResponse().getContentAsString();
        List<String> questionIds = JsonPath.read(questionsResponse, "$.data[*].id");
        String questionJson = questionIds.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
        String answerJson = questionIds.stream().limit(3)
                .map(id -> "{\"questionId\":\"" + id + "\",\"answer\":\"测试回答\"}")
                .collect(java.util.stream.Collectors.joining(","));

        String applicationResponse = mvc.perform(put("/api/v1/recruitment/me")
                        .header("Authorization", bearer(visitorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "name":"新成员大王","major":"计算机科学","className":"计科 2501","grade":"2025",
                                  "email":"new@yes-lab.internal","phone":"13800138000","wechat":"yeslab-new","selfIntroduction":"喜欢机器人与视觉研究。","interestDirections":["无人机"],
                                  "existingSkills":["Python"],"experience":"参加过校级机器人项目", "intendedTags":["无人机系统"],
                                  "portfolioIntroduction":"我的无人机作品","mediaLinks":[{"platform":"GitHub","account":"visitor","url":"https://github.com/example"}],
                                  "technicalQuestionIds":[%s],"technicalAnswers":[%s]
                                }
                                """).formatted(questionJson, answerJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("SIGNUP"))
                .andExpect(jsonPath("$.data.email").value("new@yes-lab.internal"))
                .andExpect(jsonPath("$.data.phone").value("13800138000"))
                .andExpect(jsonPath("$.data.wechat").value("yeslab-new"))
                .andExpect(jsonPath("$.data.selfIntroduction").value("喜欢机器人与视觉研究。"))
                .andExpect(jsonPath("$.data.technicalQuestions.length()").value(5))
                .andExpect(jsonPath("$.data.technicalAnswers.length()").value(3))
                .andExpect(jsonPath("$.data.history.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String applicationId = JsonPath.read(applicationResponse, "$.data.id");

        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        mvc.perform(get("/api/v1/admin/recruitment/applications").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].applicantUsername", hasItem("visitorflow@example.com")))
                .andExpect(jsonPath("$.data[*].phone", hasItem("13800138000")))
                .andExpect(jsonPath("$.data[*].selfIntroduction", hasItem("喜欢机器人与视觉研究。")));

        mvc.perform(put("/api/v1/admin/recruitment/applications/{id}/password", applicationId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"visitorflow@example.com","password":"Visitor-Flow-2026!"}
                                """))
                .andExpect(status().isUnauthorized());
        login("visitorflow@example.com", "yeslab521");

        changeStage(applicationId, teacherToken, "SCREENING");
        mvc.perform(put("/api/v1/admin/recruitment/applications/{id}/interview", applicationId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"interviewerUsername":"core","score":88,"evaluation":"工程基础扎实",
                                 "suggestedTags":["无人机系统","工程实现"],"passed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("INTERVIEW"))
                .andExpect(jsonPath("$.data.interview.score").value(88));

        changeStage(applicationId, teacherToken, "SKILL_TEST");
        changeStage(applicationId, teacherToken, "PROBATION");
        mvc.perform(post("/api/v1/admin/recruitment/applications/{id}/convert", applicationId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberCode":"S-FLOW-001","skillTags":["无人机系统","工程实现"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("FORMAL_MEMBER"))
                .andExpect(jsonPath("$.data.convertedMemberId").isNotEmpty())
                .andExpect(jsonPath("$.data.history.length()").value(6));

        mvc.perform(put("/api/v1/admin/recruitment/applications/{id}/password", applicationId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("该报名账号已转为正式成员，请在成员管理中重置密码"));

        String memberToken = login("VISITORFLOW@EXAMPLE.COM", "yeslab521");
        mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCode").value("S-FLOW-001"))
                .andExpect(jsonPath("$.data.skillTags", hasItem("无人机系统")));
    }

    @Test
    void screeningDecisionNotifiesApplicantsWithTheCorrectNextStep() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        ApplicantFixture passed = registerApplicantWithApplication(
                "screening-pass@example.com", "ScreenPass1", "初筛通过同学"
        );
        ApplicantFixture rejected = registerApplicantWithApplication(
                "screening-retry@example.com", "ScreenRetry1", "下次再来同学"
        );

        changeStage(passed.applicationId(), teacherToken, "SCREENING");
        mvc.perform(patch("/api/v1/admin/recruitment/applications/{id}/stage", passed.applicationId())
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"INTERVIEW","note":"自定义旧文案","linkedQuizId":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("INTERVIEW"))
                .andExpect(jsonPath("$.data.history[*].note", hasItem("初筛通过，请前往“我的报名”预约面试")));
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(passed.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.messages[0].type").value("SCREENING_PASSED"))
                .andExpect(jsonPath("$.data.messages[0].title").value("初筛已通过，请预约面试"))
                .andExpect(jsonPath("$.data.messages[0].summary", containsString("选择合适的面试场次")))
                .andExpect(jsonPath("$.data.messages[0].targetPath").value("/application"));

        changeStage(rejected.applicationId(), teacherToken, "SCREENING");
        mvc.perform(patch("/api/v1/admin/recruitment/applications/{id}/stage", rejected.applicationId())
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"REJECTED","note":"自定义旧文案","linkedQuizId":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("REJECTED"))
                .andExpect(jsonPath("$.data.history[*].note", hasItem("本轮初筛暂未通过，建议补充相关基础知识后再次报名")));
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(rejected.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.messages[0].type").value("SCREENING_NOT_PASSED"))
                .andExpect(jsonPath("$.data.messages[0].title").value("本轮初筛结果"))
                .andExpect(jsonPath("$.data.messages[0].summary", containsString("补充基础知识")))
                .andExpect(jsonPath("$.data.messages[0].summary", containsString("下一次招新时再次报名")))
                .andExpect(jsonPath("$.data.messages[0].targetPath").value("/application"));
    }

    @Test
    void registrationRequiresEmailAndPasswordBetweenSixAndEighteenCharacters() throws Exception {
        for (String username : List.of("plain-visitor-name", "+86 138-0013-8000", "broken@example")) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"Valid123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.username").value("请输入有效的邮箱"));
        }
        for (String password : List.of("12345", "1234567890123456789")) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"bad-length@example.com\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.password").value("密码长度需为 6—18 位"));
        }
        for (String password : List.of("123456", "123456789012345678")) {
            String email = "length-" + password.length() + "@example.com";
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + email.toUpperCase() + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.account.username").value(email));
            login(email, password);
        }
    }

    @Test
    void memberProfileSanitizesRichTextOnTheServer() throws Exception {
        String memberToken = login("member", "YesLab-Member-2026!");
        mvc.perform(put("/api/v1/member/profile")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"internalContact":"member@yes-lab.internal","headline":"具身智能研究",
                                 "profileHtml":"<script>alert(1)</script><p><strong>安全内容</strong></p><a href='javascript:alert(2)'>危险链接</a>"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileHtml", containsString("安全内容")))
                .andExpect(jsonPath("$.data.profileHtml", not(containsString("script"))))
                .andExpect(jsonPath("$.data.profileHtml", not(containsString("javascript:"))));
    }

    @Test
    void rememberLoginIsExplicitAndRefreshTokensRotate() throws Exception {
        var sessionLogin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"member","password":"YesLab-Member-2026!","rememberMe":false}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        Cookie sessionCookie = sessionLogin.getCookie("yeslab_refresh_token");
        org.assertj.core.api.Assertions.assertThat(sessionCookie).isNotNull();
        org.assertj.core.api.Assertions.assertThat(sessionCookie.getMaxAge()).isEqualTo(-1);
        org.assertj.core.api.Assertions.assertThat(sessionCookie.isHttpOnly()).isTrue();

        var rememberedLogin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"member","password":"YesLab-Member-2026!","rememberMe":true}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        Cookie firstCookie = rememberedLogin.getCookie("yeslab_refresh_token");
        org.assertj.core.api.Assertions.assertThat(firstCookie).isNotNull();
        org.assertj.core.api.Assertions.assertThat(firstCookie.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
        org.assertj.core.api.Assertions.assertThat(rememberedLogin.getHeader("Set-Cookie"))
                .contains("HttpOnly", "SameSite=Lax");

        var refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(firstCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse();
        Cookie rotatedCookie = refreshed.getCookie("yeslab_refresh_token");
        org.assertj.core.api.Assertions.assertThat(rotatedCookie).isNotNull();
        org.assertj.core.api.Assertions.assertThat(rotatedCookie.getValue()).isNotEqualTo(firstCookie.getValue());

        mvc.perform(post("/api/v1/auth/refresh").cookie(firstCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Set-Cookie", containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/logout").cookie(rotatedCookie))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").cookie(rotatedCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountCanChangeOwnPasswordAndExistingRefreshSessionsAreRevoked() throws Exception {
        var registration = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"password-change@example.com","password":"OldPass1"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        String accessToken = tokenFrom(registration.getContentAsString());
        Cookie refreshCookie = registration.getCookie("yeslab_refresh_token");
        org.assertj.core.api.Assertions.assertThat(refreshCookie).isNotNull();

        mvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrong-password","newPassword":"NewPass1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前密码不正确"));

        mvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"OldPass1","newPassword":"NewPass1"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"password-change@example.com","password":"OldPass1"}
                                """))
                .andExpect(status().isUnauthorized());
        login("password-change@example.com", "NewPass1");
    }

    @Test
    void adminCanManageMembersAndPublicProfilesHidePrivateTeacherFields() throws Exception {
        String memberToken = login("member", "YesLab-Member-2026!");
        mvc.perform(get("/api/v1/admin/members").header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden());

        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String membersResponse = mvc.perform(get("/api/v1/admin/members")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data[*].username", hasItem("member")))
                .andReturn().getResponse().getContentAsString();

        java.util.List<String> memberIds = JsonPath.read(membersResponse, "$.data[?(@.username == 'member')].id");
        String memberId = memberIds.getFirst();
        mvc.perform(put("/api/v1/admin/members/{id}", memberId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"范桌轩大王","memberCode":"S-001","role":"MEMBER",
                                 "major":"具身智能工程","className":"人工智能 2401","grade":"2024",
                                 "internalContact":"member@yes-lab.internal","status":"OFFICIAL",
                                 "skillTags":["具身智能","机器人控制"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.major").value("具身智能工程"))
                .andExpect(jsonPath("$.data.skillTags", hasItem("机器人控制")));

        java.util.List<String> teacherIds = JsonPath.read(membersResponse, "$.data[?(@.role == 'TEACHER')].id");
        mvc.perform(get("/api/v1/public/member-profiles/{id}", teacherIds.getFirst()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("汤洪大王"))
                .andExpect(jsonPath("$.data.major").value(nullValue()))
                .andExpect(jsonPath("$.data.totalPoints").value(nullValue()))
                .andExpect(jsonPath("$.data.memberCode").doesNotExist())
                .andExpect(jsonPath("$.data.internalContact").doesNotExist());
    }

    @Test
    void adminCanCreateCoreStudentAccountWithSystemAdminPermissions() throws Exception {
        String memberToken = login("member", "YesLab-Member-2026!");
        String request = """
                {
                  "username":"student-admin@example.com","temporaryPassword":"Student-Admin-2026!",
                  "name":"学生管理员大王","memberCode":"S-ADMIN-001","major":"计算机科学",
                  "className":"计科 2401","grade":"2024","internalContact":"student-admin@example.com",
                  "status":"OFFICIAL","skillTags":["无人机系统","项目管理"]
                }
                """;

        mvc.perform(post("/api/v1/admin/members/core-students")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());

        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String createdResponse = mvc.perform(post("/api/v1/admin/members/core-students")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("student-admin@example.com"))
                .andExpect(jsonPath("$.data.role").value("CORE_STUDENT"))
                .andExpect(jsonPath("$.data.memberCode").value("S-ADMIN-001"))
                .andReturn().getResponse().getContentAsString();
        String createdProfileId = JsonPath.read(createdResponse, "$.data.id");

        mvc.perform(put("/api/v1/admin/members/{id}/password", createdProfileId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/members/{id}/password", createdProfileId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"student-admin@example.com","password":"Student-Admin-2026!"}
                                """))
                .andExpect(status().isUnauthorized());

        String studentAdminToken = login("student-admin@example.com", "yeslab521");
        mvc.perform(get("/api/v1/admin/members").header("Authorization", bearer(studentAdminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void memberCanUploadReplaceAndRemoveAvatar() throws Exception {
        String memberToken = login("member", "YesLab-Member-2026!");
        String profileResponse = mvc.perform(get("/api/v1/member/profile")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String profileId = JsonPath.read(profileResponse, "$.data.id");
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};
        MockMultipartFile avatar = new MockMultipartFile("avatar", "avatar.png", "image/png", png);

        mvc.perform(multipart("/api/v1/member/profile/avatar")
                        .file(avatar)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl", containsString("/api/v1/public/member-profiles/" + profileId + "/avatar?v=")));

        mvc.perform(get("/api/v1/public/member-profiles/{profileId}/avatar", profileId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/png"));

        mvc.perform(delete("/api/v1/member/profile/avatar")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(nullValue()));
        mvc.perform(get("/api/v1/public/member-profiles/{profileId}/avatar", profileId))
                .andExpect(status().isNotFound());
    }

    private void changeStage(String applicationId, String token, String stage) throws Exception {
        mvc.perform(patch("/api/v1/admin/recruitment/applications/{id}/stage", applicationId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"" + stage + "\",\"note\":\"测试流转\",\"linkedQuizId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value(stage));
    }

    private ApplicantFixture registerApplicantWithApplication(String email, String password, String name) throws Exception {
        String registration = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = tokenFrom(registration);
        String questionsResponse = mvc.perform(get("/api/v1/recruitment/me/questions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> questionIds = JsonPath.read(questionsResponse, "$.data[*].id");
        String questionJson = questionIds.stream().map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        String answerJson = questionIds.stream().limit(3)
                .map(id -> "{\"questionId\":\"" + id + "\",\"answer\":\"测试回答\"}")
                .collect(java.util.stream.Collectors.joining(","));
        String application = mvc.perform(put("/api/v1/recruitment/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "name":"%s","major":"计算机科学","className":"计科 2501","grade":"2025",
                                  "email":"%s","interestDirections":["机器人"],"existingSkills":["Python"],
                                  "intendedTags":["机器人控制"],"mediaLinks":[],
                                  "technicalQuestionIds":[%s],"technicalAnswers":[%s]
                                }
                                """).formatted(name, email, questionJson, answerJson)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new ApplicantFixture(token, JsonPath.read(application, "$.data.id"));
    }

    private record ApplicantFixture(String token, String applicationId) { }

    private String login(String username, String password) throws Exception {
        String content = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tokenFrom(content);
    }

    private String tokenFrom(String response) {
        return JsonPath.read(response, "$.data.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

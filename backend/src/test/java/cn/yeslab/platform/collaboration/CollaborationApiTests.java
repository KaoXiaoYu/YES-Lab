package cn.yeslab.platform.collaboration;

import cn.yeslab.platform.identity.repository.AccountRepository;
import cn.yeslab.platform.recruitment.model.RecruitmentApplicationEntity;
import cn.yeslab.platform.recruitment.model.RecruitmentStage;
import cn.yeslab.platform.recruitment.repository.InterviewSessionRepository;
import cn.yeslab.platform.recruitment.repository.RecruitmentApplicationRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CollaborationApiTests {

    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private RecruitmentApplicationRepository applications;
    @Autowired private InterviewSessionRepository sessions;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void interviewSessionsKeepInterviewerIdentityPrivateAndMoveNoShowsToTheTail() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String firstEmail = "interview-one-" + suffix + "@example.com";
        String secondEmail = "interview-two-" + suffix + "@example.com";
        String firstToken = register(firstEmail);
        String secondToken = register(secondEmail);
        createInterviewApplication(firstEmail, "候选人甲");
        createInterviewApplication(secondEmail, "候选人乙");

        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        Instant startAt = Instant.now().plusSeconds(2 * 3600);
        Instant endAt = startAt.plusSeconds(3600);
        String sessionRequest = ("""
                {"startAt":"%s","endAt":"%s","location":"工科楼 A205","capacity":4,
                 "interviewerUsernames":["teacher","core"]}
                """).formatted(startAt, endAt);

        mvc.perform(post("/api/v1/admin/recruitment/interview-sessions")
                        .header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"startAt":"%s","endAt":"%s","location":"工科楼 A205","capacity":4,
                                 "interviewerUsernames":["core"]}
                                """).formatted(startAt, endAt)))
                .andExpect(status().isBadRequest());

        String created = mvc.perform(post("/api/v1/admin/recruitment/interview-sessions")
                        .header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(sessionRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interviewers", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        String sessionId = JsonPath.read(created, "$.data.id");

        mvc.perform(get("/api/v1/recruitment/interviews").header("Authorization", bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableSessions[0].id").value(sessionId))
                .andExpect(jsonPath("$.data.availableSessions[0].location").doesNotExist())
                .andExpect(jsonPath("$.data.availableSessions[0].interviewers").doesNotExist());

        mvc.perform(post("/api/v1/recruitment/interviews/sessions/{id}/book", sessionId)
                        .header("Authorization", bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booking.queueNumber").value(1))
                .andExpect(jsonPath("$.data.booking.location").value("工科楼 A205"));
        mvc.perform(post("/api/v1/recruitment/interviews/sessions/{id}/book", sessionId)
                        .header("Authorization", bearer(firstToken)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/recruitment/interviews/sessions/{id}/book", sessionId)
                        .header("Authorization", bearer(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booking.queueNumber").value(2));
        mvc.perform(delete("/api/v1/recruitment/interviews/booking").header("Authorization", bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booking").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(post("/api/v1/recruitment/interviews/sessions/{id}/book", sessionId)
                        .header("Authorization", bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booking.queueNumber").value(3));

        var session = sessions.findById(UUID.fromString(sessionId)).orElseThrow();
        session.update(session.getInterviewers(), Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                session.getLocation(), session.getCapacity());
        sessions.saveAndFlush(session);

        String called = mvc.perform(post("/api/v1/admin/recruitment/interview-sessions/{id}/call-next", sessionId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queue[0].status").value("CALLED"))
                .andReturn().getResponse().getContentAsString();
        String noShowBookingId = JsonPath.read(called, "$.data.queue[0].bookingId");

        mvc.perform(post("/api/v1/admin/recruitment/interview-sessions/{id}/bookings/{bookingId}/no-show",
                        sessionId, noShowBookingId).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queue[1].queueNumber").value(4))
                .andExpect(jsonPath("$.data.queue[1].status").value("WAITING"));
        String secondCalled = mvc.perform(post("/api/v1/admin/recruitment/interview-sessions/{id}/call-next", sessionId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queue[0].queueNumber").value(3))
                .andExpect(jsonPath("$.data.queue[0].status").value("CALLED"))
                .andReturn().getResponse().getContentAsString();
        String secondBookingId = JsonPath.read(secondCalled, "$.data.queue[0].bookingId");

        mvc.perform(post("/api/v1/admin/recruitment/interview-sessions/{id}/bookings/{bookingId}/start",
                        sessionId, secondBookingId).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queue[0].status").value("IN_PROGRESS"));
        mvc.perform(post("/api/v1/admin/recruitment/interview-sessions/{id}/bookings/{bookingId}/complete",
                        sessionId, secondBookingId).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":86,\"evaluation\":\"动手能力扎实\",\"suggestedTags\":[\"工程实现\"],\"passed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queue[0].status").value("COMPLETED"));

        mvc.perform(get("/api/v1/recruitment/interviews").header("Authorization", bearer(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booking.queueNumber").value(4))
                .andExpect(jsonPath("$.data.booking.currentlyCalledNumber").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(post("/api/v1/admin/recruitment/interview-sessions/{id}/end-early", sessionId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED_EARLY"))
                .andExpect(jsonPath("$.data.queue", hasSize(1)));
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].senderName").value("梅琳娜"))
                .andExpect(jsonPath("$.data.messages[0].type").value("INTERVIEW_RELEASED"));
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].type").value("INTERVIEW_PASSED"))
                .andExpect(jsonPath("$.data.messages[0].summary").value("动手能力扎实"));
    }

    @Test
    void discussionRepliesAndLikesProduceReadOnlyAggregatedBotNotifications() throws Exception {
        String coreToken = login("core", "YesLab-Core-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");

        String postResponse = mvc.perform(post("/api/v1/discussions")
                        .header("Authorization", bearer(coreToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"飞控参数讨论\",\"content\":\"如何统一新机型的调参记录？\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String postId = JsonPath.read(postResponse, "$.data.id");

        mvc.perform(patch("/api/v1/discussions/{id}/like", postId).header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(1));
        mvc.perform(patch("/api/v1/discussions/{id}/like", postId).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(2));
        mvc.perform(post("/api/v1/discussions/{id}/replies", postId)
                        .header("Authorization", bearer(memberToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"建议按机架版本建立参数模板。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replies", hasSize(1)));

        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(coreToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andExpect(jsonPath("$.data.messages[?(@.type == 'DISCUSSION_LIKE')].aggregationCount", hasItem(2)))
                .andExpect(jsonPath("$.data.messages[?(@.type == 'DISCUSSION_LIKE')].title", hasItem("你的讨论获得了 2 个赞")));

        mvc.perform(post("/api/v1/notifications").header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/v1/discussions/{id}", postId).header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden());
    }

    private void createInterviewApplication(String username, String name) {
        var applicant = accounts.findByUsernameIgnoreCase(username).orElseThrow();
        var application = new RecruitmentApplicationEntity(applicant, name, "计算机科学", "计科 2501", "2025",
                username, List.of("机器人"), List.of("Java"), "项目经历", List.of("工程实现"));
        application.changeStage(RecruitmentStage.INTERVIEW);
        applications.saveAndFlush(application);
    }

    private String register(String username) throws Exception {
        String content = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Interview-2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return tokenFrom(content);
    }

    private String login(String username, String password) throws Exception {
        String content = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return tokenFrom(content);
    }

    private String tokenFrom(String response) { return JsonPath.read(response, "$.data.accessToken"); }
    private String bearer(String token) { return "Bearer " + token; }
}

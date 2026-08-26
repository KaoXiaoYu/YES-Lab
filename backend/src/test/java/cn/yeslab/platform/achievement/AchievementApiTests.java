package cn.yeslab.platform.achievement;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AchievementApiTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void captainSubmitsCertificateAndImagesThenAdminPublishesDetail() throws Exception {
        String memberToken = login("member", "YesLab-Member-2026!");
        String profileResponse = mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String memberId = JsonPath.read(profileResponse, "$.data.id");

        MockMultipartFile data = jsonPart("""
                {
                  "name":"全国具身智能挑战赛","track":"空地协同赛道","level":"NATIONAL",
                  "lifecycle":"FINISHED","awardName":"全国二等奖",
                  "description":"团队使用无人机与机器狗完成自主侦察、路径规划和协同任务。",
                  "competitionDate":"2026-08-20","participants":[],
                  "imageDescriptions":["团队在比赛现场完成系统联调"]
                }
                """);
        MockMultipartFile certificate = new MockMultipartFile("certificate", "certificate.png", "image/png", pngBytes());
        MockMultipartFile image = new MockMultipartFile("images", "现场.png", "image/png", pngBytes());

        String created = mvc.perform(multipart("/api/v1/competitions")
                        .file(data).file(certificate).file(image).header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captain.id").value(memberId))
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.hasCertificate").value(true))
                .andExpect(jsonPath("$.data.images[0].description").value("团队在比赛现场完成系统联调"))
                .andReturn().getResponse().getContentAsString();
        String competitionId = JsonPath.read(created, "$.data.id");

        mvc.perform(get("/api/v1/public/competitions/{id}", competitionId))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/admin/achievements/competitions/{id}/review", competitionId)
                        .header("Authorization", bearer(memberToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"note\":\"越权审核\"}"))
                .andExpect(status().isForbidden());

        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        mvc.perform(patch("/api/v1/admin/achievements/competitions/{id}/review", competitionId)
                        .header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"note\":\"请补充技术过程说明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"));
        mvc.perform(put("/api/v1/competitions/{id}", competitionId)
                        .header("Authorization", bearer(memberToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(data.getBytes()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"));
        mvc.perform(patch("/api/v1/admin/achievements/competitions/{id}/review", competitionId)
                        .header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"note\":\"证书与成员信息核验通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("APPROVED"));
        mvc.perform(patch("/api/v1/admin/achievements/competitions/{id}/display", competitionId)
                        .header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"featured\":true,\"displayOrder\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featured").value(true));

        String publicDetail = mvc.perform(get("/api/v1/public/competitions/{id}", competitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("团队使用无人机与机器狗完成自主侦察、路径规划和协同任务。"))
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String publicImageUrl = JsonPath.read(publicDetail, "$.data.images[0].url");
        mvc.perform(get(publicImageUrl)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/v1/public/competitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", hasItem("全国具身智能挑战赛")));
        mvc.perform(get("/api/v1/public/member-profiles/{id}", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.achievementRecords", hasItem("全国具身智能挑战赛 · 全国二等奖")));
    }

    @Test
    void captainCanRegisterAnUnfinishedCompetitionWithDatesAndInstructor() throws Exception {
        String memberToken = login("member", "YesLab-Member-2026!");
        String options = mvc.perform(get("/api/v1/competitions/member-options")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<String> teacherIds = JsonPath.read(options, "$.data[?(@.role == 'TEACHER')].id");

        MockMultipartFile data = jsonPart("""
                {
                  "name":"2027 智能机器人大赛","track":"飞行巡航定点赛道","level":"PROVINCIAL",
                  "lifecycle":"PLANNED","description":"正在组队并开展飞控与视觉算法训练。",
                  "provincialDate":"2027-05-10","nationalDate":"2027-08-12",
                  "advisorProfileId":"%s","participants":[]
                }
                """.formatted(teacherIds.getFirst()));

        mvc.perform(multipart("/api/v1/competitions").file(data).header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycle").value("PLANNED"))
                .andExpect(jsonPath("$.data.verificationStatus").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.data.advisor.role").value("TEACHER"))
                .andExpect(jsonPath("$.data.nationalDate").value("2027-08-12"));
    }

    @Test
    void adminMaintainsExternalNewsAndPublicFeedUsesDateOrder() throws Exception {
        String memberToken = login("member", "YesLab-Member-2026!");
        mvc.perform(post("/api/v1/admin/achievements/news")
                        .header("Authorization", bearer(memberToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(newsPayload("普通成员不应创建", "2026-08-01")))
                .andExpect(status().isForbidden());

        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        mvc.perform(post("/api/v1/admin/achievements/news")
                        .header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(newsPayload("学校官网报道 YES Lab 获奖", "2026-07-01")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/achievements/news")
                        .header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(newsPayload("学校公众号报道空地协同团队", "2026-08-22")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/public/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("学校公众号报道空地协同团队"))
                .andExpect(jsonPath("$.data[1].title").value("学校官网报道 YES Lab 获奖"));
    }

    private MockMultipartFile jsonPart(String json) {
        return new MockMultipartFile("data", "data.json", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] pngBytes() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};
    }

    private String newsPayload(String title, String date) {
        return """
                {"title":"%s","sourceName":"宜春学院官网","sourceUrl":"https://example.edu.cn/news/yes-lab",
                 "summary":"外部网站对 YES Lab 竞赛成果的报道摘要。","publishedDate":"%s","visible":true}
                """.formatted(title, date);
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

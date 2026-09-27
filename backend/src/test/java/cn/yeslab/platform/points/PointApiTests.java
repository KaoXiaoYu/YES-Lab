package cn.yeslab.platform.points;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class PointApiTests {

    private static final ZoneId LAB_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;
    private LocalDate labToday;

    @BeforeEach
    void setUp() {
        // 积分周期使用北京时间；测试数据不能依赖 CI 机器的默认时区。
        labToday = LocalDate.now(LAB_TIME_ZONE);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void adminCanGrantSharedProjectPointsAndMemberCanReadLedger() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");
        String memberId = profileId(memberToken);
        String coreToken = login("core", "YesLab-Core-2026!");
        String coreId = profileId(coreToken);
        String source = "PROJECT:SLAM:" + System.nanoTime();

        mvc.perform(post("/api/v1/admin/points/grants")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectGrant(source, memberId, coreId)))
                .andExpect(status().isForbidden());

        String grantResponse = mvc.perform(post("/api/v1/admin/points/grants")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectGrant(source, memberId, coreId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("PROJECT"))
                .andExpect(jsonPath("$.data.subcategory").value("PROJECT_TASK"))
                .andExpect(jsonPath("$.data.itemTotalPoints").value(50))
                .andExpect(jsonPath("$.data.awardedPoints").value(50))
                .andExpect(jsonPath("$.data.allocations[*].creditedPoints", hasItem(30)))
                .andExpect(jsonPath("$.data.allocations[*].creditedPoints", hasItem(20)))
                .andReturn().getResponse().getContentAsString();
        String grantId = JsonPath.read(grantResponse, "$.data.id");

        mvc.perform(get("/api/v1/member/points").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPoints").value(30))
                .andExpect(jsonPath("$.data.totalRank").value(1))
                .andExpect(jsonPath("$.data.participantCount").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.categoryTotals.PROJECT").value(30))
                .andExpect(jsonPath("$.data.entries[0].grantId").value(grantId))
                .andExpect(jsonPath("$.data.entries[0].points").value(30))
                .andExpect(jsonPath("$.data.dailyPoints[0].date").value(labToday.minusDays(1).toString()))
                .andExpect(jsonPath("$.data.dailyPoints[0].points").value(30));

        mvc.perform(post("/api/v1/admin/points/grants")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectGrant(source, memberId, coreId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("该积分来源已经发放，请勿重复提交"));

        mvc.perform(post("/api/v1/admin/points/grants/{grantId}/reversal", grantId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"验收记录关联错误，撤销后重新登记\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("REVERSAL"))
                .andExpect(jsonPath("$.data.reversalOfGrantId").value(grantId))
                .andExpect(jsonPath("$.data.awardedPoints").value(-50));

        mvc.perform(get("/api/v1/member/points").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPoints").value(0))
                .andExpect(jsonPath("$.data.categoryTotals.PROJECT").value(0));

        mvc.perform(post("/api/v1/admin/points/grants/{grantId}/reversal", grantId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"重复撤销\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void competitionUsesPerMemberPointsAndMonthlyCapsUseOccurrenceMonth() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");
        String memberId = profileId(memberToken);
        String coreToken = login("core", "YesLab-Core-2026!");
        String coreId = profileId(coreToken);
        String day = labToday.minusDays(1).toString();

        mvc.perform(post("/api/v1/admin/points/grants")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"全国竞赛二等奖","subcategory":"COMPETITION_AWARD",
                                  "occurredOn":"%s","itemTotalPoints":400,
                                  "sourceReference":"COMPETITION:TEST:%s","evidenceUrl":"https://example.com/award",
                                  "allocations":[
                                    {"memberProfileId":"%s","points":400,"contribution":"完成竞赛作品开发并进入获奖名单"},
                                    {"memberProfileId":"%s","points":400,"contribution":"完成现场调试并进入获奖名单"}
                                  ]
                                }
                                """.formatted(day, System.nanoTime(), memberId, coreId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemTotalPoints").value(400))
                .andExpect(jsonPath("$.data.awardedPoints").value(800));

        String capSource = "LAB:CAP:" + System.nanoTime();
        mvc.perform(post("/api/v1/admin/points/grants")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleGrant("LAB_ACTIVITY", 80, capSource, memberId, day)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/points/grants")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleGrant("LAB_ACTIVITY", 30, capSource + ":OVER", memberId, day)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allocations[0].requestedPoints").value(30))
                .andExpect(jsonPath("$.data.allocations[0].creditedPoints").value(20));
        mvc.perform(post("/api/v1/admin/points/grants")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleGrant("LAB_ACTIVITY", 10, capSource + ":FULL", memberId, day)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已达到 100 分上限")));

        mvc.perform(get("/api/v1/points/rules").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.subcategory == 'LAB_ACTIVITY')].monthlyCap", hasItem(100)))
                .andExpect(jsonPath("$.data[?(@.subcategory == 'MEDIA_CONTENT')].monthlyCap", hasItem(nullValue())));
    }

    @Test
    void adminCannotGrantPointsForFutureLabDate() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");
        String memberId = profileId(memberToken);

        mvc.perform(post("/api/v1/admin/points/grants")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleGrant("MEDIA_CONTENT", 25, "FUTURE:TEST:" + System.nanoTime(),
                                memberId, labToday.plusDays(1).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.occurredOn").exists());

        mvc.perform(get("/api/v1/member/points").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPoints").value(0));
    }

    @Test
    void memberLeaderboardIncludesAllOfficialStudentsAcrossPeriods() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");
        String memberId = profileId(memberToken);
        String today = labToday.toString();

        mvc.perform(post("/api/v1/admin/points/grants")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleGrant(
                                "MEDIA_CONTENT",
                                25,
                                "LEADERBOARD:TEST:" + System.nanoTime(),
                                memberId,
                                today
                        )))
                .andExpect(status().isOk());

        for (String period : new String[]{"TOTAL", "DAY", "WEEK", "MONTH", "YEAR"}) {
            mvc.perform(get("/api/v1/points/leaderboard")
                            .param("period", period)
                            .header("Authorization", bearer(memberToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.period").value(period))
                    .andExpect(jsonPath("$.data.entries.length()").value(greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.data.entries[0].memberName").value("范桌轩大王"))
                    .andExpect(jsonPath("$.data.entries[0].points").value(25))
                    .andExpect(jsonPath("$.data.entries[0].rank").value(1))
                    .andExpect(jsonPath("$.data.entries[0].currentMember").value(true))
                    .andExpect(jsonPath("$.data.entries[1].points").value(0))
                    .andExpect(jsonPath("$.data.entries[*].role", org.hamcrest.Matchers.not(hasItem("TEACHER"))));
        }
    }

    private String projectGrant(String source, String memberId, String coreId) {
        return """
                {
                  "title":"部署 SLAM 任务","subcategory":"PROJECT_TASK",
                  "occurredOn":"%s","itemTotalPoints":50,
                  "sourceReference":"%s","evidenceUrl":"https://example.com/projects/slam",
                  "description":"任务开始前已登记，验收通过。",
                  "allocations":[
                    {"memberProfileId":"%s","points":30,"contribution":"完成部署配置与数据采集"},
                    {"memberProfileId":"%s","points":20,"contribution":"完成复现测试与验收记录"}
                  ]
                }
                """.formatted(labToday.minusDays(1), source, memberId, coreId);
    }

    private String singleGrant(String subcategory, int points, String source, String memberId, String day) {
        return """
                {
                  "title":"实验室贡献","subcategory":"%s","occurredOn":"%s",
                  "itemTotalPoints":%d,"sourceReference":"%s","evidenceUrl":"https://example.com/evidence",
                  "allocations":[{"memberProfileId":"%s","points":%d,"contribution":"按排班完成实验室公共事务"}]
                }
                """.formatted(subcategory, day, points, source, memberId, points);
    }

    private String profileId(String token) throws Exception {
        String response = mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

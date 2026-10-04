package cn.openlims.platform.points;

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

import cn.openlims.platform.identity.repository.MemberProfileRepository;
import cn.openlims.platform.project.repository.ProjectTeamRepository;
import cn.openlims.platform.project.model.*;
import cn.openlims.platform.achievement.repository.CompetitionRepository;
import cn.openlims.platform.achievement.model.*;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    @Autowired private MemberProfileRepository profiles;
    @Autowired private ProjectTeamRepository projects;
    @Autowired private CompetitionRepository competitions;
    private String projectId;
    private String competitionId;

    @BeforeEach
    void setUp() {
        // 积分周期使用北京时间；测试数据不能依赖 CI 机器的默认时区。
        labToday = LocalDate.now(LAB_TIME_ZONE);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var member = profiles.findAll().stream().filter(p -> p.getAccount().getUsername().equals("member")).findFirst().orElseThrow();
        var core = profiles.findAll().stream().filter(p -> p.getAccount().getUsername().equals("core")).findFirst().orElseThrow();
        var project = new ProjectTeamEntity("积分联动测试项目", "测试队", "测试描述", ProjectType.RESEARCH,
                ProjectStatus.ACTIVE, member, null, member.getAccount());
        project.updateTeam("测试队", member, java.util.Set.of(member, core), java.util.Set.of());
        projectId = projects.saveAndFlush(project).getId().toString();
        var competition = new CompetitionEntity("积分联动测试竞赛", CompetitionLevel.NATIONAL,
                CompetitionLifecycle.FINISHED, "测试描述", member, member.getAccount());
        competition.updateDetails("积分联动测试竞赛", null, CompetitionLevel.NATIONAL, CompetitionLifecycle.FINISHED,
                "二等奖", "测试描述", labToday, null, null, null, null, null);
        competition.replaceParticipants(List.of(new CompetitionParticipantEntity(core.getName(), core, false, 1)));
        competition.updateCertificate("fixture.jpg", "fixture.jpg", "image/jpeg", 1, false);
        competition.review(VerificationStatus.APPROVED, "审核通过", member.getAccount());
        competitionId = competitions.saveAndFlush(competition).getId().toString();
    }

    @Test
    void adminCanGrantSharedProjectPointsAndMemberCanReadLedger() throws Exception {
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");
        String memberToken = login("member", "OpenLIMS-Member-2026!");
        String memberId = profileId(memberToken);
        String coreToken = login("core", "OpenLIMS-Core-2026!");
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(grantId));

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
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");
        String memberToken = login("member", "OpenLIMS-Member-2026!");
        String memberId = profileId(memberToken);
        String coreToken = login("core", "OpenLIMS-Core-2026!");
        String coreId = profileId(coreToken);
        String day = labToday.minusDays(1).toString();

        mvc.perform(post("/api/v1/admin/points/grants")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"全国竞赛二等奖","subcategory":"COMPETITION_AWARD",
                                  "occurredOn":"%s","itemTotalPoints":400,
                                  "requestKey":"%s","competitionId":"%s",
                                  "allocations":[
                                    {"memberProfileId":"%s","points":400,"contribution":"完成竞赛作品开发并进入获奖名单"},
                                    {"memberProfileId":"%s","points":400,"contribution":"完成现场调试并进入获奖名单"}
                                  ]
                                }
                                """.formatted(day, UUID.randomUUID(), competitionId, memberId, coreId)))
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
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");
        String memberToken = login("member", "OpenLIMS-Member-2026!");
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
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");
        String memberToken = login("member", "OpenLIMS-Member-2026!");
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
                    .andExpect(jsonPath("$.data.entries[0].memberName").value("示例成员"))
                    .andExpect(jsonPath("$.data.entries[0].points").value(25))
                    .andExpect(jsonPath("$.data.entries[0].rank").value(1))
                    .andExpect(jsonPath("$.data.entries[0].currentMember").value(true))
                    .andExpect(jsonPath("$.data.entries[1].points").value(0))
                    .andExpect(jsonPath("$.data.entries[*].role", org.hamcrest.Matchers.not(hasItem("TEACHER"))));
        }
    }

    @Test
    void linkedSourcesRejectOutsidersAndUnreviewedAwardsWithoutPartialCredit() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = profileId(login("member", "OpenLIMS-Member-2026!"));
        String core = profileId(login("core", "OpenLIMS-Core-2026!"));
        mvc.perform(get("/api/v1/admin/points/sources").header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.competitions[?(@.id=='" + competitionId + "')].members[*].id", hasItem(core)));
        var project = projects.findById(UUID.fromString(projectId)).orElseThrow();
        project.updateTeam("测试队", project.getLeader(), java.util.Set.of(project.getLeader()), java.util.Set.of());
        projects.saveAndFlush(project);
        mvc.perform(post("/api/v1/admin/points/grants").header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(projectGrant("outsider", member, core)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/member/points").header("Authorization", bearer(login("member", "OpenLIMS-Member-2026!"))))
                .andExpect(jsonPath("$.data.totalPoints").value(0));
        var award = competitions.findById(UUID.fromString(competitionId)).orElseThrow();
        award.markPending();
        competitions.saveAndFlush(award);
        mvc.perform(post("/api/v1/admin/points/grants").header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(linkedGrant("COMPETITION_AWARD", "competitionId", competitionId, member, UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/points/sources").header("Authorization", bearer(teacher)))
                .andExpect(jsonPath("$.data.competitions[?(@.id=='" + competitionId + "')]").isEmpty());
    }

    @Test
    void sourceTypeAndExistenceAreValidatedEvenWhenClientBypassesThePicker() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = profileId(login("member", "OpenLIMS-Member-2026!"));
        for (String body : List.of(
                singleGrant("PROJECT_TASK", 25, "missing-source", member, labToday.toString()),
                linkedGrant("PROJECT_TASK", "competitionId", competitionId, member, UUID.randomUUID().toString()),
                linkedGrant("PROJECT_TASK", "projectId", UUID.randomUUID().toString(), member, UUID.randomUUID().toString()),
                linkedGrant("MEDIA_CONTENT", "projectId", projectId, member, UUID.randomUUID().toString()))) {
            var response = mvc.perform(post("/api/v1/admin/points/grants").header("Authorization", bearer(teacher))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
            org.assertj.core.api.Assertions.assertThat(response.getStatus()).isIn(400, 404);
        }
    }

    @Test
    void retryKeepsOriginalBatchAndChangedPayloadCannotReuseItsKey() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = profileId(login("member", "OpenLIMS-Member-2026!"));
        String body = linkedGrant("PROJECT_TASK", "projectId", projectId, member, UUID.randomUUID().toString());
        String response = mvc.perform(post("/api/v1/admin/points/grants").header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceName").value("积分联动测试项目"))
                .andExpect(jsonPath("$.data.sourceEntityReference").value(projectId))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.data.id");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(response, "$.data.sourceReference"))
                .matches("PTS-\\d{17}-[0-9a-f-]{36}");
        mvc.perform(post("/api/v1/admin/points/grants").header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(post("/api/v1/admin/points/grants").header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("事项测试", "变更事项")))
                .andExpect(status().isConflict());
    }

    @Test
    void publicHomeUsesRealIdentityAndScoresAndReversalUpdatesAllThreeBoards() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = profileId(login("member", "OpenLIMS-Member-2026!"));
        String response = mvc.perform(post("/api/v1/admin/points/grants").header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleGrant("MEDIA_CONTENT", 25, "public-real", member, labToday.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String board : List.of("总榜", "月榜", "年榜")) {
            mvc.perform(get("/api/v1/public/rankings").param("board", board))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].memberSlug").value(member))
                    .andExpect(jsonPath("$.data[0].points").value(25))
                    .andExpect(jsonPath("$.data[0].memberCode").doesNotExist())
                    .andExpect(jsonPath("$.data[0].username").doesNotExist());
        }
        mvc.perform(get("/api/v1/public/home")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rankings.length()").value(3))
                .andExpect(jsonPath("$.data.rankings['总榜'][0].memberSlug").value(member));
        mvc.perform(get("/api/v1/public/rankings").param("board", "无人机")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/points/grants/{id}/reversal", (String) JsonPath.read(response, "$.data.id"))
                        .header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"积分更正\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/rankings"))
                .andExpect(jsonPath("$.data[*].points", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(0))))
                .andExpect(jsonPath("$.data[*].rank", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(1))));
    }

    private String linkedGrant(String category, String sourceField, String sourceId, String memberId, String key) {
        return """
                {"title":"事项测试","subcategory":"%s","occurredOn":"%s","itemTotalPoints":25,
                 "requestKey":"%s","%s":"%s","description":"保留事项说明",
                 "allocations":[{"memberProfileId":"%s","points":25,"contribution":"关联成员贡献"}]}
                """.formatted(category, labToday, key, sourceField, sourceId, memberId);
    }

    private String key(String source) { return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString(); }

    private String projectGrant(String source, String memberId, String coreId) {
        return """
                {
                  "title":"部署 SLAM 任务","subcategory":"PROJECT_TASK",
                  "occurredOn":"%s","itemTotalPoints":50,
                  "requestKey":"%s","projectId":"%s",
                  "description":"任务开始前已登记，验收通过。",
                  "allocations":[
                    {"memberProfileId":"%s","points":30,"contribution":"完成部署配置与数据采集"},
                    {"memberProfileId":"%s","points":20,"contribution":"完成复现测试与验收记录"}
                  ]
                }
                """.formatted(labToday.minusDays(1), key(source), projectId, memberId, coreId);
    }

    private String singleGrant(String subcategory, int points, String source, String memberId, String day) {
        return """
                {
                  "title":"实验室贡献","subcategory":"%s","occurredOn":"%s",
                  "itemTotalPoints":%d,"requestKey":"%s",
                  "allocations":[{"memberProfileId":"%s","points":%d,"contribution":"按排班完成实验室公共事务"}]
                }
                """.formatted(subcategory, day, points, key(source), memberId, points);
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

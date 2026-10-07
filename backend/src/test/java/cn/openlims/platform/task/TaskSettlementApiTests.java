package cn.openlims.platform.task;

import cn.openlims.platform.task.service.TaskSettlementService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务到期结算：只发已通过的、到期才发、幂等、待结算队列。
 *
 * <p>结算没有对外接口（设计如此），因此这里直接调用 {@link TaskSettlementService}；
 * 而「结束任务同步结算」的链路已经在 {@code TaskApiTests} 里覆盖。</p>
 */
@SpringBootTest
@Transactional
class TaskSettlementApiTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TaskSettlementService settlementService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void dueTaskGrantsApprovedOnlyAndIsIdempotent() throws Exception {
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");
        String memberToken = login("member", "OpenLIMS-Member-2026!");
        String coreToken = login("core", "OpenLIMS-Core-2026!");

        int memberBefore = memberPoints(memberToken);
        int coreBefore = memberPoints(coreToken);

        // 先在有效期内提交与审核（到期后提交会被冻结守卫拦下），再把截止日期推到过去。
        String memberProfile = memberProfileId(teacherToken, "S-001");
        String coreProfile = memberProfileId(teacherToken, "S-CORE-001");
        String taskId = createTask(teacherToken, "到期结算任务", 30, LocalDate.now().plusDays(7),
                List.of(memberProfile, coreProfile));
        String memberAssignment = assignmentFor(teacherToken, taskId, "S-001");
        String coreAssignment = assignmentFor(teacherToken, taskId, "S-CORE-001");
        submit(memberToken, memberAssignment, "已完成");
        submit(coreToken, coreAssignment, "只提交不审核");
        review(teacherToken, taskId, memberAssignment, "APPROVED");
        review(teacherToken, taskId, coreAssignment, "REJECTED", "不达标");

        // 审核通过本身不发分。
        assertThat(memberPoints(memberToken)).isEqualTo(memberBefore);

        moveDeadlineToPast(teacherToken, taskId, "到期结算任务");

        // 待结算队列里能看到这个任务。
        List<String> due = settlementService.findDue(LocalDate.now()).stream().map(UUID::toString).toList();
        assertThat(due).contains(taskId);

        TaskSettlementService.TaskSettlementSummary summary =
                settlementService.settle(UUID.fromString(taskId));
        assertThat(summary.settled()).isTrue();
        assertThat(summary.grantedCount()).isEqualTo(1);
        assertThat(summary.notApprovedCount()).isEqualTo(1);

        // 只有已通过的人拿到积分：核心学生只是提交、随后被驳回，一分不发。
        assertThat(memberPoints(memberToken)).isEqualTo(memberBefore + 30);
        assertThat(memberPoints(coreToken)).isEqualTo(coreBefore);

        // 结算后不再出现在待结算队列里，重复结算也不重复计分。
        assertThat(settlementService.findDue(LocalDate.now())).doesNotContain(UUID.fromString(taskId));
        TaskSettlementService.TaskSettlementSummary again =
                settlementService.settle(UUID.fromString(taskId));
        assertThat(again.settled()).isFalse();
        assertThat(memberPoints(memberToken)).isEqualTo(memberBefore + 30);

        // 完成情况里能看到计分结果。
        mvc.perform(get("/api/v1/admin/tasks/{id}/progress", taskId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.awardedPointsTotal").value(30))
                .andExpect(jsonPath("$.data.assignments[?(@.memberCode=='S-001')].awardedPoints",
                        org.hamcrest.Matchers.hasItem(30)));
    }

    @Test
    void taskThatIsNotDueYetIsNotSettled() throws Exception {
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");
        String memberToken = login("member", "OpenLIMS-Member-2026!");

        int before = memberPoints(memberToken);
        // 截止日期在未来的任务，即使已经审核通过也不结算。
        String taskId = createTask(teacherToken, "未到期任务", 40, LocalDate.now().plusDays(7),
                List.of(memberProfileId(teacherToken, "S-001")));
        String assignmentId = assignmentFor(teacherToken, taskId, "S-001");
        submit(memberToken, assignmentId, "已完成");
        review(teacherToken, taskId, assignmentId, "APPROVED");

        TaskSettlementService.TaskSettlementSummary summary =
                settlementService.settle(UUID.fromString(taskId));
        assertThat(summary.settled()).isFalse();
        assertThat(summary.reason()).isEqualTo("任务尚未到期");
        assertThat(memberPoints(memberToken)).isEqualTo(before);
        assertThat(settlementService.findDue(LocalDate.now())).doesNotContain(UUID.fromString(taskId));
    }

    @Test
    void zeroPointTaskNeverEntersSettlementQueue() throws Exception {
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");
        String memberToken = login("member", "OpenLIMS-Member-2026!");

        int before = memberPoints(memberToken);
        String taskId = createTask(teacherToken, "不计分到期任务", 0, LocalDate.now().plusDays(7),
                List.of(memberProfileId(teacherToken, "S-001")));
        String assignmentId = assignmentFor(teacherToken, taskId, "S-001");
        submit(memberToken, assignmentId, "已完成");
        review(teacherToken, taskId, assignmentId, "APPROVED");

        assertThat(settlementService.findDue(LocalDate.now())).doesNotContain(UUID.fromString(taskId));
        TaskSettlementService.TaskSettlementSummary summary =
                settlementService.settle(UUID.fromString(taskId));
        assertThat(summary.settled()).isFalse();
        assertThat(summary.reason()).isEqualTo("该任务未绑定积分");
        assertThat(memberPoints(memberToken)).isEqualTo(before);
    }

    @Test
    void closingTaskSettlesItImmediately() throws Exception {
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");
        String memberToken = login("member", "OpenLIMS-Member-2026!");

        int before = memberPoints(memberToken);
        String taskId = createTask(teacherToken, "结束即结算任务", 18, LocalDate.now().plusDays(30),
                List.of(memberProfileId(teacherToken, "S-001")));
        String assignmentId = assignmentFor(teacherToken, taskId, "S-001");
        submit(memberToken, assignmentId, "已完成");
        review(teacherToken, taskId, assignmentId, "APPROVED");
        assertThat(memberPoints(memberToken)).isEqualTo(before);

        // 结束任务 = 到期：接口内同步结算，不必等调度周期。
        mvc.perform(post("/api/v1/admin/tasks/{id}/close", taskId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        assertThat(memberPoints(memberToken)).isEqualTo(before + 18);
    }

    /**
     * 已结算的任务被延长截止日期后重新进入待结算：延长期内新完成的人能补到积分，
     * 已经发过的人不会重复计分。
     */
    @Test
    void extendingDeadlineAfterSettlementGrantsOnlyNewCompleters() throws Exception {
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");
        String memberToken = login("member", "OpenLIMS-Member-2026!");
        String coreToken = login("core", "OpenLIMS-Core-2026!");

        int memberBefore = memberPoints(memberToken);
        int coreBefore = memberPoints(coreToken);

        String taskId = createTask(teacherToken, "延期补结算任务", 21, LocalDate.now().plusDays(7),
                List.of(memberProfileId(teacherToken, "S-001"), memberProfileId(teacherToken, "S-CORE-001")));
        String memberAssignment = assignmentFor(teacherToken, taskId, "S-001");
        String coreAssignment = assignmentFor(teacherToken, taskId, "S-CORE-001");

        // 第一轮：只有 member 完成并通过，任务到期结算。
        submit(memberToken, memberAssignment, "已完成");
        review(teacherToken, taskId, memberAssignment, "APPROVED");
        setDeadline(teacherToken, taskId, "延期补结算任务", LocalDate.now().minusDays(1));
        assertThat(settlementService.settle(UUID.fromString(taskId)).grantedCount()).isEqualTo(1);
        assertThat(memberPoints(memberToken)).isEqualTo(memberBefore + 21);
        assertThat(memberPoints(coreToken)).isEqualTo(coreBefore);
        mvc.perform(get("/api/v1/admin/tasks/{id}", taskId).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsSettledAt", notNullValue()));

        // 延长截止日期：结算标记被重置，任务重新变成「未结算」。
        // 注意此时它还不该出现在 findDue 里——findDue 同时要求「已到期」，延长后要到新的截止日期才算到期。
        setDeadline(teacherToken, taskId, "延期补结算任务", LocalDate.now().plusDays(14));
        mvc.perform(get("/api/v1/admin/tasks/{id}", taskId).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsSettledAt").doesNotExist());
        assertThat(settlementService.findDue(LocalDate.now())).doesNotContain(UUID.fromString(taskId));

        // 第二轮：core 在延长期内完成并通过，再次到期结算时只有他新拿到积分，member 走幂等复用。
        submit(coreToken, coreAssignment, "延期后完成");
        review(teacherToken, taskId, coreAssignment, "APPROVED");
        setDeadline(teacherToken, taskId, "延期补结算任务", LocalDate.now().minusDays(1));
        TaskSettlementService.TaskSettlementSummary second =
                settlementService.settle(UUID.fromString(taskId));
        assertThat(second.grantedCount()).isEqualTo(1);
        assertThat(second.reusedCount()).isEqualTo(1);
        assertThat(memberPoints(coreToken)).isEqualTo(coreBefore + 21);
        assertThat(memberPoints(memberToken)).isEqualTo(memberBefore + 21);
    }

    /** 截止日期已过时阻止成员新提交，但保留审核与迟到计分。 */
    @Test
    void expiredStandardTaskBlocksMemberSubmissionButAllowsReviewAndLatePoints() throws Exception {
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");
        String memberToken = login("member", "OpenLIMS-Member-2026!");
        int before = memberPoints(memberToken);

        String taskId = createTask(teacherToken, "到期冻结任务", 12, LocalDate.now().plusDays(7),
                List.of(memberProfileId(teacherToken, "S-001")));
        String assignmentId = assignmentFor(teacherToken, taskId, "S-001");
        submit(memberToken, assignmentId, "截止前提交的内容");

        setDeadline(teacherToken, taskId, "到期冻结任务", LocalDate.now().minusDays(1));

        // 到期后成员不能提交。
        mvc.perform(post("/api/v1/tasks/{id}/submission", assignmentId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"到期后提交\"}"))
                .andExpect(status().isConflict());
        // 任务到期时对象仍待审，第一次结算不计分；后续迟到审核通过应幂等补发。
        assertThat(settlementService.settle(UUID.fromString(taskId)).notApprovedCount()).isEqualTo(1);
        // 到期后仍可审核截止前的提交，迟到通过应照常计分。
        mvc.perform(put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review", taskId, assignmentId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk());
        assertThat(memberPoints(memberToken)).isEqualTo(before + 12);
        assertThat(settlementService.settle(UUID.fromString(taskId)).grantedCount()).isZero();
    }

    /** 不设截止日期 = 永不到期：不会结算，成员随时可以提交。 */
    @Test
    void taskWithoutDeadlineNeverExpires() throws Exception {
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");
        String memberToken = login("member", "OpenLIMS-Member-2026!");

        int before = memberPoints(memberToken);
        String taskId = createTask(teacherToken, "无截止任务", 9, null,
                List.of(memberProfileId(teacherToken, "S-001")));
        String assignmentId = assignmentFor(teacherToken, taskId, "S-001");

        submit(memberToken, assignmentId, "随时可交");
        review(teacherToken, taskId, assignmentId, "APPROVED");

        assertThat(settlementService.findDue(LocalDate.now())).doesNotContain(UUID.fromString(taskId));
        TaskSettlementService.TaskSettlementSummary summary =
                settlementService.settle(UUID.fromString(taskId));
        assertThat(summary.settled()).isFalse();
        assertThat(summary.reason()).isEqualTo("任务尚未到期");
        assertThat(memberPoints(memberToken)).isEqualTo(before);
    }

    /** 结束任务是不可逆终局：连改（包括延长截止日期）都不允许。 */
    @Test
    void closedTaskCannotBeModifiedAnymore() throws Exception {
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");

        String taskId = createTask(teacherToken, "终局任务", 0, LocalDate.now().plusDays(7),
                List.of(memberProfileId(teacherToken, "S-001")));
        closeTask(teacherToken, taskId);

        mvc.perform(put("/api/v1/admin/tasks/{id}", taskId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"终局任务\",\"contentHtml\":\"<p>任务正文</p>\","
                                + "\"startDate\":\"2026-09-01\",\"endDate\":\"2026-12-31\","
                                + "\"points\":0,\"subtasks\":[],\"rules\":[],\"memberProfileIds\":[]}"))
                .andExpect(status().isConflict());
    }

    /** 结算把站内任务路径写成凭证，并给任务创建者发一条结算汇总。 */
    @Test
    void settlementUsesStationTaskPathAsEvidenceAndNotifiesCreator() throws Exception {
        String teacherToken = login("teacher", "OpenLIMS-Teacher-2026!");
        String memberToken = login("member", "OpenLIMS-Member-2026!");

        String profileId = memberProfileId(teacherToken, "S-001");
        String taskId = createTask(teacherToken, "凭证与通知任务", 11, LocalDate.now().plusDays(7),
                List.of(profileId));
        String assignmentId = assignmentFor(teacherToken, taskId, "S-001");
        submit(memberToken, assignmentId, "已完成");
        review(teacherToken, taskId, assignmentId, "APPROVED");
        // 先审核（到期后审核会被冻结），再把截止日期移到过去触发结算。
        setDeadline(teacherToken, taskId, "凭证与通知任务", LocalDate.now().minusDays(1));
        assertThat(settlementService.settle(UUID.fromString(taskId)).grantedCount()).isEqualTo(1);

        // 积分凭证是站内任务路径（审核表单里填的外部链接在结算口径下不再使用）。
        mvc.perform(get("/api/v1/admin/points/grants").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.sourceReference=='TASK:" + taskId + ":" + profileId + "')].evidenceUrl",
                        hasItem("/tasks/" + assignmentId)));

        // 创建者收到结算汇总。
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[?(@.title=='任务积分已结算')].summary",
                        hasItem(org.hamcrest.Matchers.containsString("凭证与通知任务"))));
    }

    // ---------- 辅助 ----------

    /** 结束任务：CLOSED 即到期，接口会同步触发一次积分结算。 */
    private void closeTask(String token, String taskId) throws Exception {
        mvc.perform(post("/api/v1/admin/tasks/{id}/close", taskId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    /** 把已发布任务的截止日期改到昨天，用来模拟「任务自然到期」。 */
    private void moveDeadlineToPast(String token, String taskId, String title) throws Exception {
        setDeadline(token, taskId, title, LocalDate.now().minusDays(1));
    }

    /**
     * 修改已发布任务的截止日期。
     *
     * <p>到期后成员不能再提交，所以「先在未来交完、再把截止日期移到过去」是模拟自然到期的唯一方式。
     * 已发布任务只能改内容与时间，因此请求里的积分与条件字段会被服务端忽略。</p>
     */
    private void setDeadline(String token, String taskId, String title, LocalDate endDate) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"contentHtml\":\"<p>任务正文</p>\","
                + "\"startDate\":\"2026-09-01\",\"endDate\":\"" + endDate + "\","
                + "\"points\":0,\"subtasks\":[],\"rules\":[],\"memberProfileIds\":[]}";
        mvc.perform(put("/api/v1/admin/tasks/{id}", taskId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private String createTask(String token, String title, int points, LocalDate endDate, List<String> memberProfileIds)
            throws Exception {
        String ids = memberProfileIds.stream().map(id -> "\"" + id + "\"").reduce((a, b) -> a + "," + b).orElse("");
        // endDate 为 null 表示不设截止日期（永不到期）。
        String endJson = endDate == null ? "" : ",\"endDate\":\"" + endDate + "\"";
        String created = mvc.perform(post("/api/v1/admin/tasks")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"%s","contentHtml":"<p>任务正文</p>","startDate":"2026-09-01"%s,
                                 "points":%d,
                                 "subtasks":[{"title":"第一步"}],
                                 "rules":[],"memberProfileIds":[%s]}
                                """).formatted(title, endJson, points, ids)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(created, "$.data.id");
        mvc.perform(post("/api/v1/admin/tasks/{id}/publish", taskId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        return taskId;
    }

    /**
     * 按成员编号精确指派，而不是用「角色条件」。
     *
     * <p>测试共用同一个 H2 上下文，其它测试类可能留下额外的成员档案；用条件匹配会让对象数随
     * 全局成员集合浮动，断言就只能放宽。这里显式指定成员，让用例与全局数据解耦。</p>
     */
    private String memberProfileId(String token, String memberCode) throws Exception {
        String members = mvc.perform(get("/api/v1/admin/members").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(members,
                "$.data[?(@.memberCode=='" + memberCode + "')].id");
        assertThat(ids).as("成员 %s 的档案", memberCode).isNotEmpty();
        return ids.getFirst();
    }

    private void submit(String token, String assignmentId, String note) throws Exception {
        mvc.perform(post("/api/v1/tasks/{id}/submission", assignmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"" + note + "\"}"))
                .andExpect(status().isOk());
    }

    private void review(String token, String taskId, String assignmentId, String decision) throws Exception {
        review(token, taskId, assignmentId, decision, null);
    }

    private void review(String token, String taskId, String assignmentId, String decision, String comment)
            throws Exception {
        String body = comment == null
                ? "{\"decision\":\"" + decision + "\"}"
                : "{\"decision\":\"" + decision + "\",\"comment\":\"" + comment + "\"}";
        mvc.perform(put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review", taskId, assignmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private String assignmentFor(String token, String taskId, String memberCode) throws Exception {
        String progress = mvc.perform(get("/api/v1/admin/tasks/{id}/progress", taskId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(progress,
                "$.data.assignments[?(@.memberCode=='" + memberCode + "')].assignmentId");
        assertThat(ids).as("成员 %s 的任务对象", memberCode).isNotEmpty();
        return ids.getFirst();
    }

    private int memberPoints(String token) throws Exception {
        String summary = mvc.perform(get("/api/v1/member/points").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(summary, "$.data.totalPoints");
    }

    private String login(String username, String password) throws Exception {
        String content = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(content, "$.data.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

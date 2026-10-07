package cn.openlims.platform.task;

import cn.openlims.platform.task.service.BountyService;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 悬赏任务：自主接取与名额、完成名次与奖金不变量（含驳回顺延）、提交即完成、到期结算与冻结。
 */
@SpringBootTest
@Transactional
class BountyApiTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BountyService bountyService;

    /** 用于把悬赏的截止日期直接改到过去，模拟「自然到期」：接口只允许延长，改不出过期状态。 */
    @Autowired
    private cn.openlims.platform.task.repository.TaskRepository taskRepository;

    @Autowired
    private TaskSettlementService settlementService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---------- 接取与名额 ----------

    @Test
    void claimOccupiesHeadcountAndRejectsSecondClaim() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = login("member", "OpenLIMS-Member-2026!");
        String core = login("core", "OpenLIMS-Core-2026!");

        String taskId = createBounty(teacher, "名额 1 的悬赏", null, null, 1, 10, LocalDate.now().plusDays(7));
        publish(teacher, taskId);

        // 第一位接取成功：占用 1 个名额。
        mvc.perform(post("/api/v1/bounties/{id}/claim", taskId).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.claimed").value(1));

        // 名额已满，第二位被拒；悬赏榜上标出原因。
        mvc.perform(post("/api/v1/bounties/{id}/claim", taskId).header("Authorization", bearer(core)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/bounties").header("Authorization", bearer(core)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.taskId=='" + taskId + "')].claimBlockedReason",
                        hasItem("接取名额已满")));

        // 同一人不能接取两次（无论什么状态）。
        mvc.perform(post("/api/v1/bounties/{id}/claim", taskId).header("Authorization", bearer(member)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("你已接取这条悬赏"));
    }

    @Test
    void abandonReleasesHeadcountAndForbidsClaimingAgain() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = login("member", "OpenLIMS-Member-2026!");
        String core = login("core", "OpenLIMS-Core-2026!");

        String taskId = createBounty(teacher, "可放弃的悬赏", null, null, 1, 10, LocalDate.now().plusDays(7));
        publish(teacher, taskId);
        claim(member, taskId);

        mvc.perform(post("/api/v1/bounties/{id}/abandon", taskId).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimed").value(0));

        // 名额归还：别人可以接；但本人已经用掉了唯一一次机会。
        mvc.perform(post("/api/v1/bounties/{id}/claim", taskId).header("Authorization", bearer(core)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/bounties/{id}/claim", taskId).header("Authorization", bearer(member)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("你曾接取后放弃或被移除，同一条悬赏只能接取一次"));
    }

    @Test
    void unlimitedHeadcountLetsEveryoneClaim() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = login("member", "OpenLIMS-Member-2026!");
        String core = login("core", "OpenLIMS-Core-2026!");

        String taskId = createBounty(teacher, "不限人数的悬赏", null, null, null, 10, LocalDate.now().plusDays(7));
        publish(teacher, taskId);
        claim(member, taskId);
        claim(core, taskId);

        mvc.perform(get("/api/v1/admin/bounties/{id}/claims", taskId).header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headcountLimit").doesNotExist())
                .andExpect(jsonPath("$.data.claimed").value(2))
                .andExpect(jsonPath("$.data.occupied").value(2));
    }

    // ---------- 完成名次与奖金 ----------

    @Test
    void completionRanksAreSequentialAndOnlyTopSlotsGetThePrize() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = login("member", "OpenLIMS-Member-2026!");
        String core = login("core", "OpenLIMS-Core-2026!");

        // 奖金 1 份、接取不限、不绑积分（纯奖金悬赏）。
        String taskId = createBounty(teacher, "先完成先得", "机械键盘一把", 1, null, 0, LocalDate.now().plusDays(7));
        publish(teacher, taskId);
        claim(core, taskId);
        claim(member, taskId);

        String coreAssignment = assignmentFor(teacher, taskId, "S-CORE-001");
        String memberAssignment = assignmentFor(teacher, taskId, "S-001");

        // 核心学生先完成 → 第 1 名并拿到唯一一份奖金。
        complete(core, coreAssignment, "我先完成");
        mvc.perform(get("/api/v1/admin/bounties/{id}/claims", taskId).header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prizeIssued").value(1))
                .andExpect(jsonPath("$.data.rows[?(@.memberCode=='S-CORE-001')].completionRank", hasItem(1)))
                .andExpect(jsonPath("$.data.rows[?(@.memberCode=='S-CORE-001')].prizeAwarded", hasItem(true)));

        // 第二位完成 → 第 2 名，没有奖金。
        complete(member, memberAssignment, "我稍后完成");
        mvc.perform(get("/api/v1/admin/bounties/{id}/claims", taskId).header("Authorization", bearer(teacher)))
                .andExpect(jsonPath("$.data.prizeIssued").value(1))
                .andExpect(jsonPath("$.data.rows[?(@.memberCode=='S-001')].completionRank", hasItem(2)))
                .andExpect(jsonPath("$.data.rows[?(@.memberCode=='S-001')].prizeAwarded", hasItem(false)));
    }

    @Test
    void revokingPrizeHolderPassesPrizeToNextCompleter() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = login("member", "OpenLIMS-Member-2026!");
        String core = login("core", "OpenLIMS-Core-2026!");

        String taskId = createBounty(teacher, "顺延悬赏", "500 元现金", 1, null, 0, LocalDate.now().plusDays(7));
        publish(teacher, taskId);
        claim(core, taskId);
        claim(member, taskId);
        String coreAssignment = assignmentFor(teacher, taskId, "S-CORE-001");
        String memberAssignment = assignmentFor(teacher, taskId, "S-001");

        complete(core, coreAssignment, "先完成");
        complete(member, memberAssignment, "后完成");
        assertPrize(teacher, taskId, "S-CORE-001", true);
        assertPrize(teacher, taskId, "S-001", false);

        // 驳回第 1 名 → 奖金顺延给第 2 名；名次保留不重算。
        mvc.perform(post("/api/v1/admin/bounties/{id}/claims/{assignmentId}/revoke", taskId, coreAssignment)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"成果不达标\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.completionRank").value(1))
                .andExpect(jsonPath("$.data.prizeAwarded").value(false))
                .andExpect(jsonPath("$.data.prizeFulfillmentStatus").value("REVOKED"));

        assertPrize(teacher, taskId, "S-001", true);
        mvc.perform(get("/api/v1/admin/bounties/{id}/claims", taskId).header("Authorization", bearer(teacher)))
                .andExpect(jsonPath("$.data.prizeIssued").value(1))
                .andExpect(jsonPath("$.data.approved").value(1));

        // 驳回必须填意见。
        mvc.perform(post("/api/v1/admin/bounties/{id}/claims/{assignmentId}/revoke", taskId, memberAssignment)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"  \"}"))
                .andExpect(status().isBadRequest());

        // 被驳回者不可再接。
        mvc.perform(post("/api/v1/bounties/{id}/claim", taskId).header("Authorization", bearer(core)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("你的完成结果已被驳回，同一条悬赏只能接取一次"));
    }

    @Test
    void prizeFulfillmentRecordsActorsAndBlocksRejectionAfterIssue() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = login("member", "OpenLIMS-Member-2026!");
        String taskId = createBounty(teacher, "奖金履约台账", "500 元现金", 1, null, 0, LocalDate.now().plusDays(7));
        publish(teacher, taskId);
        claim(member, taskId);
        String assignmentId = assignmentFor(teacher, taskId, "S-001");
        complete(member, assignmentId, "完成说明");

        mvc.perform(get("/api/v1/tasks/{id}", assignmentId).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prizeFulfillmentStatus").value("PENDING"));

        // 结束只冻结任务参与动作，不阻止后续补录线下奖金发放/领取。
        mvc.perform(post("/api/v1/admin/bounties/{id}/close", taskId).header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // 实际线下发放后记管理员和时间；发放后拒绝驳回，完成与奖金额度均不得改变。
        mvc.perform(post("/api/v1/admin/bounties/{taskId}/claims/{assignmentId}/prize-fulfillment/issue",
                        taskId, assignmentId).header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prizeFulfillmentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.data.prizeIssuedAt", notNullValue()))
                .andExpect(jsonPath("$.data.prizeIssuedBy").value("teacher"));
        mvc.perform(post("/api/v1/admin/bounties/{id}/claims/{assignmentId}/revoke", taskId, assignmentId)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"尝试驳回\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/admin/bounties/{id}/claims", taskId).header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.rows[0].prizeAwarded").value(true));

        // 仅获奖成员本人可确认；完成后重复确认冲突，并保留确认人/时间。
        mvc.perform(post("/api/v1/tasks/{id}/bounty-prize/confirm-received", assignmentId)
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/tasks/{id}/bounty-prize/confirm-received", assignmentId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prizeFulfillmentStatus").value("RECEIVED"))
                .andExpect(jsonPath("$.data.prizeReceivedAt", notNullValue()));
        mvc.perform(post("/api/v1/tasks/{id}/bounty-prize/confirm-received", assignmentId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isConflict());
    }

    @Test
    void revokingOnlyCompleterLeavesPrizeSlotOpenUntilNextCompleter() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = login("member", "OpenLIMS-Member-2026!");
        String core = login("core", "OpenLIMS-Core-2026!");

        String taskId = createBounty(teacher, "份额空置悬赏", "证书一份", 1, null, 0, LocalDate.now().plusDays(7));
        publish(teacher, taskId);
        claim(core, taskId);
        String coreAssignment = assignmentFor(teacher, taskId, "S-CORE-001");
        complete(core, coreAssignment, "只有我完成");
        assertPrize(teacher, taskId, "S-CORE-001", true);

        // 唯一完成者被驳回：份额空置（没人可补），等下一个完成者自动获得。
        mvc.perform(post("/api/v1/admin/bounties/{id}/claims/{assignmentId}/revoke", taskId, coreAssignment)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"需要重做\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/bounties/{id}/claims", taskId).header("Authorization", bearer(teacher)))
                .andExpect(jsonPath("$.data.prizeIssued").value(0));

        claim(member, taskId);
        String memberAssignment = assignmentFor(teacher, taskId, "S-001");
        complete(member, memberAssignment, "我来完成");
        assertPrize(teacher, taskId, "S-001", true);
    }

    // ---------- 提交即完成、到期结算与冻结 ----------

    @Test
    void completionIsImmediateAndPointsAreSettledAtDeadline() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = login("member", "OpenLIMS-Member-2026!");
        String core = login("core", "OpenLIMS-Core-2026!");

        int memberBefore = memberPoints(member);
        int coreBefore = memberPoints(core);

        // 奖金 1 份 + 每人 15 积分。
        String taskId = createBounty(teacher, "奖金加积分", "定制奖杯", 1, null, 15, LocalDate.now().plusDays(7));
        publish(teacher, taskId);
        claim(core, taskId);
        String coreAssignment = assignmentFor(teacher, taskId, "S-CORE-001");

        // 提交即完成：状态直接是已通过，没有「待确认」，也不发积分。
        complete(core, coreAssignment, "完成说明");
        assertThat(memberPoints(core)).isEqualTo(coreBefore);
        mvc.perform(get("/api/v1/tasks/{id}", coreAssignment).header("Authorization", bearer(core)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.taskType").value("BOUNTY"))
                .andExpect(jsonPath("$.data.prizeAwarded").value(true))
                .andExpect(jsonPath("$.data.completionRank").value(1))
                .andExpect(jsonPath("$.data.pointsSettled").value(false));

        // 完成后再提交被拒。
        complete(core, coreAssignment, "再提交一次", 409);

        // 未到期不结算。
        assertThat(settlementService.settle(UUID.fromString(taskId)).settled()).isFalse();

        // 自然到期（截止日期已过）→ 结算只发已完成的；来源编号用 BOUNTY 前缀。
        expireByEntity(taskId);
        var summary = settlementService.settle(UUID.fromString(taskId));
        assertThat(summary.settled()).isTrue();
        assertThat(summary.grantedCount()).isEqualTo(1);
        assertThat(memberPoints(core)).isEqualTo(coreBefore + 15);
        mvc.perform(get("/api/v1/admin/points/grants").header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                // 来源编号是 BOUNTY:{taskId}:{memberProfileId}，这里用前缀匹配（悬赏必须与普通任务的 TASK: 前缀区分开）。
                .andExpect(jsonPath("$.data[?(@.sourceReference =~ /BOUNTY:" + taskId + ":.*/)].evidenceUrl",
                        hasItem("/tasks/" + coreAssignment)));

        // 到期后：不能再接取、不能再驳回。
        mvc.perform(post("/api/v1/bounties/{id}/claim", taskId).header("Authorization", bearer(member)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("已过接取期"));
        mvc.perform(post("/api/v1/admin/bounties/{id}/claims/{assignmentId}/revoke", taskId, coreAssignment)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"太晚了\"}"))
                .andExpect(status().isConflict());
        assertThat(memberPoints(member)).isEqualTo(memberBefore);
    }

    @Test
    void expiredBountyFreezesSubmissionAndAdminCanStillRemoveClaims() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = login("member", "OpenLIMS-Member-2026!");

        String taskId = createBounty(teacher, "到期冻结悬赏", "证书", 2, null, 0, LocalDate.now().plusDays(7));
        publish(teacher, taskId);
        claim(member, taskId);
        String assignmentId = assignmentFor(teacher, taskId, "S-001");
        expireByEntity(taskId);

        complete(member, assignmentId, "到期后提交", 409);

        // 到期后仍可移除接取者（清理动作，不产生结论）。
        mvc.perform(delete("/api/v1/admin/bounties/{id}/claims/{assignmentId}", taskId, assignmentId)
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.abandoned").value(1))
                .andExpect(jsonPath("$.data.occupied").value(0));
    }

    // ---------- 校验、隔离与权限 ----------

    @Test
    void rewardsMustBePairedAndHeadcountOnlyIncreases() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");

        // 奖金份数与说明必须成对。
        createBountyExpect(teacher, "只有份数", null, 2, null, 0, 400);
        createBountyExpect(teacher, "只有说明", "奖品", null, null, 0, 400);
        // 至少要有一种奖励。
        createBountyExpect(teacher, "什么都没有", null, null, null, 0, 400);
        // 奖金份数不能多于接取上限。
        createBountyExpect(teacher, "份数超过人数", "奖品", 3, 2, 0, 400);

        String taskId = createBounty(teacher, "只增不减", "奖品", 1, 2, 0, LocalDate.now().plusDays(7));
        publish(teacher, taskId);
        // 已发布：下调人数或份数被拒，上调成功。
        updateBountyExpect(teacher, taskId, "只增不减", "奖品", 1, 1, 400);
        updateBountyExpect(teacher, taskId, "只增不减", "奖品", 2, 3, 200);
        // 积分绑定后不可修改（已发布只改内容、人数与日期）。
        mvc.perform(get("/api/v1/admin/bounties/{id}", taskId).header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points").value(0));
    }

    @Test
    void bountyIsIsolatedFromStandardTaskFlowsAndGuardedByPermission() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = login("member", "OpenLIMS-Member-2026!");
        String core = login("core", "OpenLIMS-Core-2026!");

        String taskId = createBounty(teacher, "隔离悬赏", "奖品", 1, null, 5, LocalDate.now().plusDays(7));
        publish(teacher, taskId);

        // 悬赏不出现在普通任务列表里，也不能用普通任务的管理接口操作（否则会混进按等级发放流程）。
        mvc.perform(get("/api/v1/admin/tasks").header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", org.hamcrest.Matchers.not(hasItem(taskId))));
        mvc.perform(post("/api/v1/admin/tasks/{id}/close", taskId).header("Authorization", bearer(teacher)))
                .andExpect(status().isNotFound());

        // 权限：普通成员不能访问悬赏管理端；游客不能接取。
        mvc.perform(get("/api/v1/admin/bounties").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/bounties/{id}/claim", taskId))
                .andExpect(status().isUnauthorized());

        // 接取条件：只对核心学生开放时，普通成员被拒。
        // 条件属于「发布时锁定」的口径，因此必须在草稿阶段设置再发布。
        String restricted = createBounty(teacher, "限核心学生", "奖品", 1, null, 5, LocalDate.now().plusDays(7));
        mvc.perform(put("/api/v1/admin/bounties/{id}", restricted)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bountyJson("限核心学生", "奖品", 1, null, 5, LocalDate.now().plusDays(7),
                                "[{\"dimension\":\"ROLE\",\"value\":\"CORE_STUDENT\"}]")))
                .andExpect(status().isOk());
        publish(teacher, restricted);
        mvc.perform(post("/api/v1/bounties/{id}/claim", restricted).header("Authorization", bearer(member)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("你不符合这条悬赏的接取资格"));
        claim(core, restricted);
    }

    /**
     * 悬赏除奖励口径外与普通任务同口径：**已发布**也能改子任务。
     *
     * <p>按 {@code id} 同步（改名保留勾选记录）、可以新增；旧实现里已发布悬赏的 {@code subtasks}
     * 被整段忽略，界面改完看似保存成功、实际没生效。</p>
     */
    @Test
    void publishedBountySyncsSubtasksByIdLikeStandardTasks() throws Exception {
        String teacher = login("teacher", "OpenLIMS-Teacher-2026!");
        String member = login("member", "OpenLIMS-Member-2026!");

        String created = mvc.perform(post("/api/v1/admin/bounties")
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"可改子任务的悬赏","contentHtml":"<p>悬赏正文</p>","prizeDescription":"一份奖品",
                                 "prizeSlots":1,"points":5,"headcountLimit":2,"startDate":"2026-09-01","endDate":"%s",
                                 "subtasks":[{"title":"甲","contentHtml":"<p>甲的说明</p>"}],"rules":[]}
                                """).formatted(LocalDate.now().plusDays(7))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(created, "$.data.id");
        String firstSubtaskId = JsonPath.read(created, "$.data.subtasks[0].id");

        publish(teacher, taskId);
        // 列表要把奖金说明带回来：管理端编辑表单用它回填（详情用的通用 TaskView 不含悬赏字段）。
        mvc.perform(get("/api/v1/admin/bounties").header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + taskId + "')].prizeDescription", hasItem("一份奖品")));
        String claimBody = mvc.perform(post("/api/v1/bounties/{id}/claim", taskId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String assignmentId = JsonPath.read(claimBody, "$.data.assignmentId");

        // 成员勾选第一个子任务：这条勾选记录必须在管理员改子任务后仍然存在。
        mvc.perform(post("/api/v1/tasks/{a}/subtasks/{s}/submission", assignmentId, firstSubtaskId)
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentHtml\":\"<p>成员完成甲</p>\"}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/admin/bounties/{id}", taskId)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"可改子任务的悬赏","contentHtml":"<p>悬赏正文</p>","prizeDescription":"一份奖品",
                                 "prizeSlots":1,"points":5,"headcountLimit":2,"startDate":"2026-09-01","endDate":"%s",
                                 "subtasks":[{"id":"%s","title":"甲（改）","contentHtml":"<p>甲的说明</p>"},
                                             {"title":"乙","contentHtml":"<p>乙的说明</p>"}],"rules":[]}
                                """).formatted(LocalDate.now().plusDays(7), firstSubtaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtasks.length()").value(2))
                .andExpect(jsonPath("$.data.subtasks[0].id").value(firstSubtaskId))
                .andExpect(jsonPath("$.data.subtasks[0].title").value("甲（改）"))
                .andExpect(jsonPath("$.data.subtasks[1].title").value("乙"));

        // 改名后勾选记录与正文都还在（新加的子任务未勾选）。
        mvc.perform(get("/api/v1/tasks/{a}", assignmentId).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtasks.length()").value(2))
                .andExpect(jsonPath("$.data.subtasks[0].title").value("甲（改）"))
                .andExpect(jsonPath("$.data.subtasks[0].submitted").value(true))
                .andExpect(jsonPath("$.data.subtasks[1].submitted").value(false));

        // 带 id 的子任务必须属于该悬赏：挂别人的子任务被拒。
        String other = createBounty(teacher, "另一条悬赏", "奖品", 1, null, 0, LocalDate.now().plusDays(7));
        String foreignId = JsonPath.read(
                mvc.perform(get("/api/v1/admin/bounties/{id}", other).header("Authorization", bearer(teacher)))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.subtasks[0].id");
        mvc.perform(put("/api/v1/admin/bounties/{id}", taskId)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"可改子任务的悬赏","contentHtml":"<p>悬赏正文</p>","prizeDescription":"一份奖品",
                                 "prizeSlots":1,"points":5,"headcountLimit":2,"startDate":"2026-09-01","endDate":"%s",
                                 "subtasks":[{"id":"%s","title":"外来的","contentHtml":null}],"rules":[]}
                                """).formatted(LocalDate.now().plusDays(7), foreignId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("提交的子任务不属于当前任务"));
    }

    // ---------- 辅助 ----------

    private String bountyJson(
            String title, String prize, Integer slots, Integer headcount, int points, LocalDate endDate, String rules) {
        String prizeJson = prize == null ? "null" : "\"" + prize + "\"";
        String slotsJson = slots == null ? "null" : String.valueOf(slots);
        String headcountJson = headcount == null ? "null" : String.valueOf(headcount);
        return ("""
                {"title":"%s","contentHtml":"<p>悬赏正文</p>","prizeDescription":%s,"prizeSlots":%s,
                 "points":%d,"headcountLimit":%s,"startDate":"2026-09-01","endDate":"%s",
                 "subtasks":[{"title":"一步"}],"rules":%s}
                """).formatted(title, prizeJson, slotsJson, points, headcountJson, endDate, rules);
    }

    private String createBounty(
            String token, String title, String prize, Integer slots, Integer headcount, int points, LocalDate endDate)
            throws Exception {
        String created = mvc.perform(post("/api/v1/admin/bounties")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bountyJson(title, prize, slots, headcount, points, endDate, "[]")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.data.id");
    }

    private void createBountyExpect(
            String token, String title, String prize, Integer slots, Integer headcount, int points, int expected)
            throws Exception {
        mvc.perform(post("/api/v1/admin/bounties")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bountyJson(title, prize, slots, headcount, points, LocalDate.now().plusDays(7), "[]")))
                .andExpect(status().is(expected));
    }

    private void updateBountyExpect(
            String token, String taskId, String title, String prize, Integer slots, Integer headcount, int expected)
            throws Exception {
        mvc.perform(put("/api/v1/admin/bounties/{id}", taskId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bountyJson(title, prize, slots, headcount, 0, LocalDate.now().plusDays(7), "[]")))
                .andExpect(status().is(expected));
    }

    private void publish(String token, String taskId) throws Exception {
        mvc.perform(post("/api/v1/admin/bounties/{id}/publish", taskId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private void claim(String token, String taskId) throws Exception {
        mvc.perform(post("/api/v1/bounties/{id}/claim", taskId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private void complete(String token, String assignmentId, String note) throws Exception {
        complete(token, assignmentId, note, 200);
    }

    private void complete(String token, String assignmentId, String note, int expected) throws Exception {
        mvc.perform(post("/api/v1/tasks/{id}/submission", assignmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"" + note + "\"}"))
                .andExpect(status().is(expected));
    }

    /**
     * 把悬赏的截止日期直接改到昨天，模拟「自然到期」。
     *
     * <p>接口层面悬赏的截止日期**只能延长**（`requireNotShortened`），因此造不出「已过期但未结束」
     * 的状态；这里沿用仓库既有的做法直接改写实体，走的是与定时任务相同的到期判定路径。</p>
     */
    private void expireByEntity(String taskId) {
        var task = taskRepository.findById(UUID.fromString(taskId)).orElseThrow();
        task.updateBountyDetails(
                task.getTitle(),
                task.getContentHtml(),
                task.getPrizeDescription(),
                task.getPrizeSlots(),
                task.getHeadcountLimit(),
                task.getStartDate(),
                LocalDate.now().minusDays(1));
        taskRepository.saveAndFlush(task);
    }

    private void assertPrize(String token, String taskId, String memberCode, boolean expected) throws Exception {
        mvc.perform(get("/api/v1/admin/bounties/{id}/claims", taskId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[?(@.memberCode=='" + memberCode + "')].prizeAwarded",
                        hasItem(expected)));
    }

    private String assignmentFor(String token, String taskId, String memberCode) throws Exception {
        String claims = mvc.perform(get("/api/v1/admin/bounties/{id}/claims", taskId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(claims, "$.data.rows[?(@.memberCode=='" + memberCode + "')].assignmentId");
        assertThat(ids).as("成员 %s 的悬赏接取", memberCode).isNotEmpty();
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

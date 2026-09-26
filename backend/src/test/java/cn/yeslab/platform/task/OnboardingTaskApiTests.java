package cn.yeslab.platform.task;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 新手任务：所有技能测试阶段报名者共享同一个大任务，管理员在大任务上直接增删子任务即时生效，
 * 子任务全部完成后才能提交与转正。
 */
@SpringBootTest
class OnboardingTaskApiTests {

    private static final ZoneId LAB_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private static final List<String> RESET_SUBTASKS = List.of(
            "配置开发环境（Git、Python 或 Java、代码编辑器）",
            "阅读实验室新人手册与安全规范",
            "认识实验室常用的无人机与机器狗设备，了解基本安全操作",
            "跑通一个示例程序或仿真环境",
            "在讨论板发布一条自我介绍"
    );

    @Autowired
    private WebApplicationContext context;

    /** 用于把某个对象的 due_date 改到过去，模拟「本人已逾期」。 */
    @Autowired
    private cn.yeslab.platform.task.repository.TaskAssignmentRepository assignmentRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // 新手任务大任务是全局单例且测试共享同一个 H2 上下文，因此每个用例都先把它重置为已知内容，
        // 保证断言不依赖用例执行顺序。
        mvc.perform(put("/api/v1/admin/tasks/onboarding")
                        .header("Authorization", bearer(login("teacher", "YesLab-Teacher-2026!")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"新手入门任务","contentHtml":"<p>请完成下列基础训练。</p>",
                                 "durationDays":7,"subtasks":%s}
                                """).formatted(toJsonArray(RESET_SUBTASKS))))
                .andExpect(status().isOk());
    }

    @Test
    void allApplicantsShareOneBigTaskAndApprovalConvertsApplicant() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        Applicant first = registerApplicant("onboarding-pass@example.com", "Onboarding1", "新手任务通过同学");
        Applicant second = registerApplicant("onboarding-pass2@example.com", "Onboarding1", "新手任务同组同学");
        reachSkillTest(first, teacherToken);
        reachSkillTest(second, teacherToken);

        String firstView = ownTask(first.token())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.title").value("新手入门任务"))
                .andExpect(jsonPath("$.data.subtasks.length()").value(5))
                .andExpect(jsonPath("$.data.submittedSubtasks").value(0))
                .andExpect(jsonPath("$.data.totalSubtasks").value(5))
                .andExpect(jsonPath("$.data.allSubtasksSubmitted").value(false))
                .andExpect(jsonPath("$.data.endDate")
                        .value(LocalDate.now(LAB_TIME_ZONE).plusDays(7).toString()))
                .andExpect(jsonPath("$.data.overdue").value(false))
                .andReturn().getResponse().getContentAsString();
        String secondView = ownTask(second.token())
                .andReturn().getResponse().getContentAsString();

        // 两人共享同一个大任务，但各自持有独立的对象与进度。
        String taskId = JsonPath.read(firstView, "$.data.taskId");
        String firstAssignmentId = JsonPath.read(firstView, "$.data.assignmentId");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(secondView, "$.data.taskId"))
                .isEqualTo(taskId);
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(secondView, "$.data.assignmentId"))
                .isNotEqualTo(firstAssignmentId);
        List<String> subtaskIds = JsonPath.read(firstView, "$.data.subtasks[*].id");

        // 未完成全部子任务时不能提交。
        mvc.perform(post("/api/v1/recruitment/me/onboarding-task/submission")
                        .header("Authorization", bearer(first.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"只做了一部分\"}"))
                .andExpect(status().isBadRequest());

        // 勾选第一项后，自己的进度变化不影响同组的另一位报名者。
        submitSubtask(first.token(), subtaskIds.getFirst())
                .andExpect(jsonPath("$.data.submittedSubtasks").value(1));
        ownTask(second.token()).andExpect(jsonPath("$.data.submittedSubtasks").value(0));

        // 勾完全部子任务后才能提交。
        for (String subtaskId : subtaskIds) {
            submitSubtask(first.token(), subtaskId);
        }
        ownTask(first.token()).andExpect(jsonPath("$.data.allSubtasksSubmitted").value(true));
        mvc.perform(post("/api/v1/recruitment/me/onboarding-task/submission")
                        .header("Authorization", bearer(first.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"环境已配好，示例程序已跑通\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // 管理端总览能看到大任务内容与该记录。
        mvc.perform(get("/api/v1/admin/tasks/onboarding-overview")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.title").value("新手入门任务"))
                .andExpect(jsonPath("$.data.rows[*].applicantName", hasItem("新手任务通过同学")))
                .andExpect(jsonPath("$.data.rows[?(@.applicantName=='新手任务通过同学')].status",
                        hasItem("SUBMITTED")));

        // 普通成员不能访问管理端。
        String memberToken = login("member", "YesLab-Member-2026!");
        mvc.perform(get("/api/v1/admin/tasks/onboarding-overview")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/tasks/onboarding").header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden());

        // 未提交的报名者不能被直接通过。
        mvc.perform(review(teacherToken, taskId, JsonPath.read(secondView, "$.data.assignmentId"),
                        """
                        {"decision":"APPROVED","memberCode":"S-ONB-002","skillTags":["机器人控制"]}
                        """))
                .andExpect(status().isConflict());

        // 审核通过即转正。
        mvc.perform(review(teacherToken, taskId, firstAssignmentId,
                        """
                        {"decision":"APPROVED","comment":"新手任务全部完成，同意转正"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.convertedProfileId", notNullValue()));

        String memberAccessToken = login("onboarding-pass@example.com", "Onboarding1");
        mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(memberAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCode").doesNotExist())
                .andExpect(jsonPath("$.data.qualificationComplete").value(false));
        mvc.perform(put("/api/v1/member/profile/qualification")
                        .header("Authorization", bearer(memberAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberCode\":\"S-ONB-001\",\"skillTags\":[\"机器人控制\",\"Python\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCode").value("S-ONB-001"))
                .andExpect(jsonPath("$.data.skillTags", hasItem("Python")))
                .andExpect(jsonPath("$.data.qualificationComplete").value(true));

        // 已通过的新手任务不能再修改。
        mvc.perform(post("/api/v1/recruitment/me/onboarding-task/submission")
                        .header("Authorization", bearer(first.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"再次提交\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectionRequiresCommentAndAllowsResubmission() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        Applicant applicant = registerApplicant("onboarding-reject@example.com", "Onboarding2", "新手任务驳回同学");
        reachSkillTest(applicant, teacherToken);

        String view = ownTask(applicant.token()).andReturn().getResponse().getContentAsString();
        String assignmentId = JsonPath.read(view, "$.data.assignmentId");
        String taskId = JsonPath.read(view, "$.data.taskId");
        completeAllSubtasks(applicant.token(), view);

        mvc.perform(put("/api/v1/recruitment/me/qualification")
                        .header("Authorization", bearer(applicant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberCode\":\"S-ONB-002\",\"skillTags\":[\"机器人控制\"]}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/recruitment/me/onboarding-task/submission")
                        .header("Authorization", bearer(applicant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"完成了大部分\"}"))
                .andExpect(status().isOk());

        // 驳回必须填写意见。
        mvc.perform(review(teacherToken, taskId, assignmentId, "{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(review(teacherToken, taskId, assignmentId,
                        "{\"decision\":\"REJECTED\",\"comment\":\"请补充仿真环境运行截图\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewComment").value("请补充仿真环境运行截图"))
                .andExpect(jsonPath("$.data.resubmissionDeadlineAt").isNotEmpty());

        // 即使原始 due_date 已过，驳回时起算的个人 24 小时仍覆盖基准截止。
        cn.yeslab.platform.task.model.TaskAssignmentEntity rejected =
                assignmentRepository.findById(UUID.fromString(assignmentId)).orElseThrow();
        rejected.assignDueDate(LocalDate.now(LAB_TIME_ZONE).minusDays(1));
        assignmentRepository.saveAndFlush(rejected);

        // 驳回后可以重新提交。
        mvc.perform(post("/api/v1/recruitment/me/onboarding-task/submission")
                        .header("Authorization", bearer(applicant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"已补充运行结果\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    /** 按人延长只影响被延长的那位报名者；共享大任务与其他人完全不变。 */
    @Test
    void extendingOneApplicantDoesNotAffectOthers() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String stamp = String.valueOf(System.nanoTime());
        Applicant first = registerApplicant("extend-a-" + stamp + "@example.com", "Extend2026", "延期甲同学");
        Applicant second = registerApplicant("extend-b-" + stamp + "@example.com", "Extend2026", "延期乙同学");
        reachSkillTest(first, teacherToken);
        reachSkillTest(second, teacherToken);

        String firstView = ownTask(first.token()).andReturn().getResponse().getContentAsString();
        String secondView = ownTask(second.token()).andReturn().getResponse().getContentAsString();
        String firstAssignment = JsonPath.read(firstView, "$.data.assignmentId");
        String secondDue = JsonPath.read(secondView, "$.data.endDate");

        LocalDate newDueDate = LocalDate.now(LAB_TIME_ZONE).plusDays(21);
        mvc.perform(put("/api/v1/admin/tasks/onboarding-assignments/{id}/due-date", firstAssignment)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueDate\":\"" + newDueDate + "\",\"reason\":\"只延甲\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dueDate").value(newDueDate.toString()));

        ownTask(first.token()).andExpect(jsonPath("$.data.endDate").value(newDueDate.toString()));
        ownTask(second.token()).andExpect(jsonPath("$.data.endDate").value(secondDue));
    }

    @Test
    void overdueApplicantCannotSubmitButAdminCanReviewExistingSubmission() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String stamp = String.valueOf(System.nanoTime());
        String memberCode = "S-EXT-" + stamp;
        Applicant applicant = registerApplicant("extend-" + stamp + "@example.com", "Extend2026", "延期同学");
        reachSkillTest(applicant, teacherToken);

        String view = ownTask(applicant.token()).andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(view, "$.data.taskId");
        String assignmentId = JsonPath.read(view, "$.data.assignmentId");

        mvc.perform(put("/api/v1/recruitment/me/qualification")
                        .header("Authorization", bearer(applicant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberCode\":\"" + memberCode + "\",\"skillTags\":[\"机器人\"]}"))
                .andExpect(status().isOk());
        completeAllSubtasks(applicant.token(), view);
        mvc.perform(post("/api/v1/recruitment/me/onboarding-task/submission")
                        .header("Authorization", bearer(applicant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"截止前已完成\"}"))
                .andExpect(status().isOk());

        // 把本人的 due_date 改到昨天：模拟「这个人已逾期」，其他人不受影响。
        cn.yeslab.platform.task.model.TaskAssignmentEntity assignment =
                assignmentRepository.findById(UUID.fromString(assignmentId)).orElseThrow();
        LocalDate overdueDate = LocalDate.now(LAB_TIME_ZONE).minusDays(1);
        assignment.assignDueDate(overdueDate);
        assignmentRepository.saveAndFlush(assignment);

        // 逾期后本人不能提交，但管理员可以审核已有提交。
        mvc.perform(post("/api/v1/recruitment/me/onboarding-task/submission")
                        .header("Authorization", bearer(applicant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"逾期提交\"}"))
                .andExpect(status().isConflict());
        mvc.perform(review(teacherToken, taskId, assignmentId, "{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void conversionGuardBlocksUnfinishedOnboardingTaskAndExemptionClosesIt() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        Applicant applicant = registerApplicant("onboarding-guard@example.com", "Onboarding3", "新手任务豁免同学");
        reachSkillTest(applicant, teacherToken);

        String applicationId = JsonPath.read(mvc.perform(get("/api/v1/recruitment/me")
                        .header("Authorization", bearer(applicant.token())))
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        // 新手任务未通过时直接转正被守卫拦下。
        mvc.perform(post("/api/v1/admin/recruitment/applications/{id}/convert", applicationId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberCode":"S-ONB-003","skillTags":["机器人控制"]}
                                """))
                .andExpect(status().isConflict());

        // 豁免理由满足任务门槛；资料缺失不再阻止直接转正。
        mvc.perform(post("/api/v1/admin/recruitment/applications/{id}/convert", applicationId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exemptionReason":"该同学在入组前已完成同等训练，经指导老师确认免修"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("FORMAL_MEMBER"))
                .andExpect(jsonPath("$.data.convertedMemberId", notNullValue()))
                .andExpect(jsonPath("$.data.history[*].note",
                        hasItem(org.hamcrest.Matchers.containsString("豁免并转正"))));
        String memberToken = login("onboarding-guard@example.com", "Onboarding3");
        mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qualificationComplete").value(false));
        mvc.perform(put("/api/v1/member/profile/qualification")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberCode\":\"S-001\",\"skillTags\":[\"机器人控制\"]}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/member/profile/qualification")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberCode\":\"S-ONB-003\",\"skillTags\":[\"机器人控制\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qualificationComplete").value(true));
    }

    @Test
    void editingSharedTaskAppliesImmediatelyAndBackfillIsIdempotent() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        Applicant applicant = registerApplicant("onboarding-edit@example.com", "Onboarding4", "新手任务编辑同学");
        reachSkillTest(applicant, teacherToken);

        String view = ownTask(applicant.token()).andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(view, "$.data.taskId");
        List<String> subtaskIds = JsonPath.read(view, "$.data.subtasks[*].id");

        // 勾选第一项，稍后验证改标题后该项勾选被保留（按 id 同步）。
        submitSubtask(applicant.token(), subtaskIds.getFirst());

        // 管理员直接改大任务：改标题、时长改 10 天、删两项、加一项，改动即时生效。
        // 保留的项带 id 提交（保留勾选），新增项不带 id。
        mvc.perform(put("/api/v1/admin/tasks/onboarding")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"新手任务（第二版）","contentHtml":"<p>更新后的说明</p>",
                                 "durationDays":10,"subtasks":[
                                   {"id":"%s","title":"%s"},
                                   {"id":"%s","title":"%s"},
                                   {"id":"%s","title":"%s"},
                                   {"title":"新增：完成一次设备安全操作演练"}]}
                                """).formatted(
                                subtaskIds.get(0), RESET_SUBTASKS.get(0),
                                subtaskIds.get(1), RESET_SUBTASKS.get(1),
                                subtaskIds.get(4), RESET_SUBTASKS.get(4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.title").value("新手任务（第二版）"))
                .andExpect(jsonPath("$.data.task.durationDays").value(10))
                // 大任务是全局共享的，退回人数取决于同上下文其他用例的提交状态，因此这里只校验本用例对象的状态变化。
                .andExpect(jsonPath("$.data.reopenedCount").isNumber())
                .andExpect(jsonPath("$.data.rescheduledCount", greaterThanOrEqualTo(1)));

        ownTask(applicant.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("新手任务（第二版）"))
                .andExpect(jsonPath("$.data.subtasks.length()").value(4))
                .andExpect(jsonPath("$.data.submittedSubtasks").value(1))
                .andExpect(jsonPath("$.data.subtasks[0].submitted").value(true))
                .andExpect(jsonPath("$.data.subtasks[*].title", hasItem("新增：完成一次设备安全操作演练")))
                .andExpect(jsonPath("$.data.endDate")
                        .value(LocalDate.now(LAB_TIME_ZONE).plusDays(10).toString()));

        // 批量补发幂等：第一次补发覆盖所有缺失的技能测试记录，连续第二次必然为 0。
        mvc.perform(post("/api/v1/admin/tasks/onboarding-tasks/backfill")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/tasks/onboarding-tasks/backfill")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issued").value(0));

        // 报名者完成当前 4 项后提交。
        String refreshed = ownTask(applicant.token()).andReturn().getResponse().getContentAsString();
        String assignmentId = JsonPath.read(refreshed, "$.data.assignmentId");
        completeAllSubtasks(applicant.token(), refreshed);
        mvc.perform(put("/api/v1/recruitment/me/qualification")
                        .header("Authorization", bearer(applicant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberCode\":\"S-ONB-004\",\"skillTags\":[\"机器人控制\"]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/recruitment/me/onboarding-task/submission")
                        .header("Authorization", bearer(applicant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"已按新要求完成\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // 管理员再新增一项子任务（保留的项带 id）：已提交待确认的对象退回「待完成」，需重新提交。
        List<String> currentIds = JsonPath.read(
                ownTask(applicant.token()).andReturn().getResponse().getContentAsString(),
                "$.data.subtasks[*].id");
        mvc.perform(put("/api/v1/admin/tasks/onboarding")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"新手任务（第二版）","contentHtml":"<p>更新后的说明</p>",
                                 "durationDays":10,"subtasks":[
                                   {"id":"%s","title":"%s"},
                                   {"id":"%s","title":"%s"},
                                   {"id":"%s","title":"%s"},
                                   {"id":"%s","title":"%s"},
                                   {"title":"新增：阅读一条安全通报并写一句心得"}]}
                                """).formatted(
                                currentIds.get(0), RESET_SUBTASKS.get(0),
                                currentIds.get(1), RESET_SUBTASKS.get(1),
                                currentIds.get(2), RESET_SUBTASKS.get(4),
                                currentIds.get(3), "新增：完成一次设备安全操作演练")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reopenedCount").value(greaterThanOrEqualTo(1)));

        ownTask(applicant.token())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.allSubtasksSubmitted").value(false))
                .andExpect(jsonPath("$.data.subtasks.length()").value(5));

        // 完成新增项后重新提交，可以正常转正。
        String reopened = ownTask(applicant.token()).andReturn().getResponse().getContentAsString();
        completeAllSubtasks(applicant.token(), reopened);
        mvc.perform(post("/api/v1/recruitment/me/onboarding-task/submission")
                        .header("Authorization", bearer(applicant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"新增项也已完成\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mvc.perform(review(teacherToken, taskId, assignmentId,
                        """
                        {"decision":"APPROVED","memberCode":"S-ONB-004","skillTags":["机器人控制"]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 大任务当前保留的子任务清单：管理员删掉的两项不再出现，新增的两项在列。
        String finalView = mvc.perform(get("/api/v1/admin/tasks/onboarding")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usingBuiltInDefault").value(false))
                .andReturn().getResponse().getContentAsString();
        List<String> finalTitles = JsonPath.read(finalView, "$.data.subtasks[*].title");
        org.assertj.core.api.Assertions.assertThat(finalTitles)
                .hasSize(5)
                .doesNotContain(RESET_SUBTASKS.get(2), RESET_SUBTASKS.get(3))
                .contains("新增：完成一次设备安全操作演练", "新增：阅读一条安全通报并写一句心得");
    }

    @Test
    void subtaskContentIsLazyLoadedAndSyncedById() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        Applicant applicant = registerApplicant("onboarding-subtask@example.com", "Onboarding5", "新手任务子任务同学");
        reachSkillTest(applicant, teacherToken);

        String adminView = mvc.perform(get("/api/v1/admin/tasks/onboarding")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtasks[0].hasContent").value(false))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(adminView, "$.data.subtasks[*].id");

        // 管理员给第一项补写富文本说明（按 id 更新）。
        mvc.perform(put("/api/v1/admin/tasks/onboarding")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"新手入门任务","contentHtml":"<p>请完成下列基础训练。</p>","durationDays":7,
                                 "subtasks":[{"id":"%s","title":"%s","contentHtml":"<p>先安装 Git 与 JDK 21。</p>"},
                                             {"id":"%s","title":"%s"},
                                             {"id":"%s","title":"%s"},
                                             {"id":"%s","title":"%s"},
                                             {"id":"%s","title":"%s"}]}
                                """).formatted(
                                ids.get(0), RESET_SUBTASKS.get(0),
                                ids.get(1), RESET_SUBTASKS.get(1),
                                ids.get(2), RESET_SUBTASKS.get(2),
                                ids.get(3), RESET_SUBTASKS.get(3),
                                ids.get(4), RESET_SUBTASKS.get(4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.subtasks[0].hasContent").value(true));

        // 大任务视图只带 hasContent，不带子任务正文。
        ownTask(applicant.token())
                .andExpect(jsonPath("$.data.subtasks[0].hasContent").value(true))
                .andExpect(jsonPath("$.data.subtasks[0].contentHtml").doesNotExist());

        // 子任务详情：正文 + 本人进度 + 大任务上下文。
        mvc.perform(get("/api/v1/recruitment/me/onboarding-task/subtasks/{id}", ids.getFirst())
                        .header("Authorization", bearer(applicant.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(RESET_SUBTASKS.get(0)))
                .andExpect(jsonPath("$.data.contentHtml", org.hamcrest.Matchers.containsString("先安装 Git")))
                .andExpect(jsonPath("$.data.taskTitle").value("新手入门任务"))
                .andExpect(jsonPath("$.data.submittedSubtasks").value(0))
                .andExpect(jsonPath("$.data.totalSubtasks").value(5))
                .andExpect(jsonPath("$.data.editable").value(true));

        // 还没有新手任务的游客打不开子任务。
        Applicant fresh = registerApplicant("onboarding-nosub@example.com", "Onboarding6", "尚未进入技能测试");
        mvc.perform(get("/api/v1/recruitment/me/onboarding-task/subtasks/{id}", ids.getFirst())
                        .header("Authorization", bearer(fresh.token())))
                .andExpect(status().isNotFound());

        // 勾选第一项后改标题（同一个 id）：勾选与正文都要保留。
        submitSubtask(applicant.token(), ids.getFirst())
                .andExpect(jsonPath("$.data.submittedSubtasks").value(1));
        mvc.perform(put("/api/v1/admin/tasks/onboarding")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"新手入门任务","contentHtml":"<p>请完成下列基础训练。</p>","durationDays":7,
                                 "subtasks":[{"id":"%s","title":"配置开发环境（改名后）","contentHtml":"<p>先安装 Git 与 JDK 21。</p>"},
                                             {"id":"%s","title":"%s"},
                                             {"id":"%s","title":"%s"},
                                             {"id":"%s","title":"%s"},
                                             {"id":"%s","title":"%s"}]}
                                """).formatted(
                                ids.get(0),
                                ids.get(1), RESET_SUBTASKS.get(1),
                                ids.get(2), RESET_SUBTASKS.get(2),
                                ids.get(3), RESET_SUBTASKS.get(3),
                                ids.get(4), RESET_SUBTASKS.get(4))))
                .andExpect(status().isOk());

        ownTask(applicant.token())
                .andExpect(jsonPath("$.data.subtasks[0].title").value("配置开发环境（改名后）"))
                .andExpect(jsonPath("$.data.subtasks[0].submitted").value(true))
                .andExpect(jsonPath("$.data.subtasks[0].hasContent").value(true));

        // 删除最后一项（提交里不再出现它的 id）。
        mvc.perform(put("/api/v1/admin/tasks/onboarding")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"新手入门任务","contentHtml":"<p>请完成下列基础训练。</p>","durationDays":7,
                                 "subtasks":[{"id":"%s","title":"配置开发环境（改名后）","contentHtml":"<p>先安装 Git 与 JDK 21。</p>"},
                                             {"id":"%s","title":"%s"},
                                             {"id":"%s","title":"%s"},
                                             {"id":"%s","title":"%s"}]}
                                """).formatted(
                                ids.get(0),
                                ids.get(1), RESET_SUBTASKS.get(1),
                                ids.get(2), RESET_SUBTASKS.get(2),
                                ids.get(3), RESET_SUBTASKS.get(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.subtasks.length()").value(4));

        ownTask(applicant.token())
                .andExpect(jsonPath("$.data.subtasks.length()").value(4))
                .andExpect(jsonPath("$.data.totalSubtasks").value(4))
                .andExpect(jsonPath("$.data.submittedSubtasks").value(1));
    }

    @Test
    void subtaskSubmissionRequiresContentAndCanBeUpdatedAndReadByAdmin() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        Applicant applicant = registerApplicant("onboarding-submit@example.com", "Onboarding7", "子任务提交同学");
        reachSkillTest(applicant, teacherToken);

        String view = ownTask(applicant.token()).andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(view, "$.data.taskId");
        String assignmentId = JsonPath.read(view, "$.data.assignmentId");
        String subtaskId = JsonPath.<List<String>>read(view, "$.data.subtasks[*].id").getFirst();

        // 空内容不允许提交
        mvc.perform(post("/api/v1/recruitment/me/onboarding-task/subtasks/{id}/submission", subtaskId)
                        .header("Authorization", bearer(applicant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentHtml\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // 提交内容会被清洗，并且详情接口能读到本人提交的内容
        mvc.perform(post("/api/v1/recruitment/me/onboarding-task/subtasks/{id}/submission", subtaskId)
                        .header("Authorization", bearer(applicant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentHtml\":\"<p onclick='evil()'>环境已配好</p><script>alert(1)</script>\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedSubtasks").value(1));
        mvc.perform(get("/api/v1/recruitment/me/onboarding-task/subtasks/{id}", subtaskId)
                        .header("Authorization", bearer(applicant.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitted").value(true))
                .andExpect(jsonPath("$.data.submittedContentHtml", org.hamcrest.Matchers.containsString("环境已配好")))
                .andExpect(jsonPath("$.data.submittedContentHtml",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("alert"))))
                .andExpect(jsonPath("$.data.submittedAt", notNullValue()));

        // 提交后可以修改并重新提交：内容被覆盖
        mvc.perform(post("/api/v1/recruitment/me/onboarding-task/subtasks/{id}/submission", subtaskId)
                        .header("Authorization", bearer(applicant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentHtml\":\"<p>第二版：换了 JDK 版本</p>\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/recruitment/me/onboarding-task/subtasks/{id}", subtaskId)
                        .header("Authorization", bearer(applicant.token())))
                .andExpect(jsonPath("$.data.submittedContentHtml",
                        org.hamcrest.Matchers.containsString("第二版")));

        // 管理端可以逐条查看该对象的子任务提交内容（未提交的子任务也在列表里）
        mvc.perform(get("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/subtasks", taskId, assignmentId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].submitted").value(true))
                .andExpect(jsonPath("$.data[0].contentHtml", org.hamcrest.Matchers.containsString("第二版")))
                .andExpect(jsonPath("$.data[1].submitted").value(false))
                .andExpect(jsonPath("$.data[1].contentHtml").doesNotExist());
    }

    // ---------- 辅助 ----------

    private org.springframework.test.web.servlet.ResultActions ownTask(String token) throws Exception {
        return mvc.perform(get("/api/v1/recruitment/me/onboarding-task")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    /** 为某个子任务提交内容（子任务不是勾选完成，而是提交一段富文本）。 */
    private org.springframework.test.web.servlet.ResultActions submitSubtask(String token, String subtaskId)
            throws Exception {
        return mvc.perform(post("/api/v1/recruitment/me/onboarding-task/subtasks/{id}/submission", subtaskId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentHtml\":\"<p>已完成该项，提交说明。</p>\"}"))
                .andExpect(status().isOk());
    }

    private void completeAllSubtasks(String token, String onboardingView) throws Exception {
        for (String subtaskId : JsonPath.<List<String>>read(onboardingView, "$.data.subtasks[*].id")) {
            submitSubtask(token, subtaskId);
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder review(
            String teacherToken, String taskId, String assignmentId, String body) {
        return put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review", taskId, assignmentId)
                .header("Authorization", bearer(teacherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void reachSkillTest(Applicant applicant, String teacherToken) throws Exception {
        mvc.perform(patch("/api/v1/admin/recruitment/applications/{id}/stage", applicant.applicationId())
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"SCREENING\",\"note\":\"测试流转\",\"linkedQuizId\":null}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/recruitment/applications/{id}/interview", applicant.applicationId())
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"interviewerUsername":"core","score":90,"evaluation":"表现良好",
                                 "suggestedTags":["机器人控制"],"passed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("INTERVIEW"));
        mvc.perform(patch("/api/v1/admin/recruitment/applications/{id}/stage", applicant.applicationId())
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"SKILL_TEST\",\"note\":\"进入技能测试\",\"linkedQuizId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("SKILL_TEST"));
    }

    private Applicant registerApplicant(String email, String password, String name) throws Exception {
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
        return new Applicant(token, JsonPath.read(application, "$.data.id"));
    }

    private static String toJsonArray(List<String> values) {
        return values.stream()
                .map(value -> "{\"title\":\"" + value + "\"}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    /** 带子任务说明的变体：第一项带富文本正文，用于验证正文保存与懒加载。 */
    private static String toJsonArrayWithContent(List<String> values, String contentHtml) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.size(); index += 1) {
            if (index > 0) builder.append(',');
            builder.append("{\"title\":\"").append(values.get(index)).append('"');
            if (index == 0) builder.append(",\"contentHtml\":\"").append(contentHtml).append('"');
            builder.append('}');
        }
        return builder.append(']').toString();
    }

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

    private record Applicant(String token, String applicationId) {
    }
}

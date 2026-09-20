package cn.yeslab.platform.task;

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

import java.util.List;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 普通任务：等级条件与预览、发布快照与积分锁定、成员提交、人工审核计分、
 * 三种不可计分情形、驳回、越权与校验。
 */
@SpringBootTest
@Transactional
class TaskApiTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void audienceRulesFollowOrWithinDimensionAndAndAcrossDimensions() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");

        // 同一维度取「或」：核心学生或教师都应命中。
        mvc.perform(post("/api/v1/admin/tasks/audience-preview")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[{"dimension":"ROLE","value":"CORE_STUDENT"},
                                          {"dimension":"ROLE","value":"TEACHER"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[*].name", hasItem("范桌轩大王")))
                .andExpect(jsonPath("$.data.members[*].role", hasItem("TEACHER")))
                .andExpect(jsonPath("$.data.members[*].role", hasItem("CORE_STUDENT")))
                .andExpect(jsonPath("$.data.members[*].role", not(hasItem("MEMBER"))));

        // 跨维度取「且」：核心学生且具备「无人机系统」标签。
        mvc.perform(post("/api/v1/admin/tasks/audience-preview")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[{"dimension":"ROLE","value":"CORE_STUDENT"},
                                          {"dimension":"SKILL_TAG","value":"无人机系统"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.members[*].memberCode", hasItem("S-CORE-001")));

        // 跨维度取「且」的反例：核心学生但没有该标签。
        mvc.perform(post("/api/v1/admin/tasks/audience-preview")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[{"dimension":"ROLE","value":"CORE_STUDENT"},
                                          {"dimension":"SKILL_TAG","value":"不存在的标签"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.members").value(empty()));

        // 教师对象在预览中标记为不可计分。
        mvc.perform(post("/api/v1/admin/tasks/audience-preview")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[{"dimension":"ROLE","value":"TEACHER"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[0].pointEligible").value(false))
                .andExpect(jsonPath("$.data.members[0].pointIneligibleReason").value("指导教师不参与成员积分统计"));

        // 已停用的成员状态不能作为发放条件。
        mvc.perform(post("/api/v1/admin/tasks/audience-preview")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[{"dimension":"MEMBER_STATUS","value":"PAUSED"}]}
                                """))
                .andExpect(status().isBadRequest());

        // 普通成员不能访问管理端。
        String memberToken = login("member", "YesLab-Member-2026!");
        mvc.perform(get("/api/v1/admin/tasks").header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishingSnapshotsAudienceAndLocksPoints() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");

        String created = mvc.perform(post("/api/v1/admin/tasks")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"整理仿真数据","contentHtml":"<p>请在截止日期前整理并提交。</p>",
                                 "startDate":"2026-09-01","endDate":"2026-09-30","points":30,
                                 "subtasks":[{"title":"导出原始数据"},{"title":"整理为表格"}],
                                 "rules":[{"dimension":"ROLE","value":"MEMBER"},
                                          {"dimension":"MEMBER_STATUS","value":"OFFICIAL"}],
                                 "memberProfileIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.taskType").value("STANDARD"))
                .andExpect(jsonPath("$.data.points").value(30))
                .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(created, "$.data.id");

        // 结束日期早于开始日期应被拒绝。
        mvc.perform(post("/api/v1/admin/tasks")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"日期非法","contentHtml":"<p>x</p>","startDate":"2026-09-30",
                                 "endDate":"2026-09-01","points":0,"subtasks":[{"title":"a"}],"rules":[],"memberProfileIds":[]}
                                """))
                .andExpect(status().isBadRequest());

        // 既没有条件也没有指定成员时不能发布。
        String emptyTask = JsonPath.read(mvc.perform(post("/api/v1/admin/tasks")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"空任务","contentHtml":"<p>x</p>","points":0,
                                 "subtasks":[{"title":"a"}],"rules":[],"memberProfileIds":[]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
        mvc.perform(post("/api/v1/admin/tasks/{id}/publish", emptyTask)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/admin/tasks/{id}/publish", taskId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.assignmentCount").value(greaterThanOrEqualTo(1)));

        // 发布后积分锁定：修改积分被忽略（沿用原值），条件也不再变更。
        mvc.perform(put("/api/v1/admin/tasks/{id}", taskId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"整理仿真数据（改）","contentHtml":"<p>更新后的正文</p>",
                                 "startDate":"2026-09-01","endDate":"2026-10-15","points":999,
                                 "subtasks":[{"title":"导出原始数据"},{"title":"整理为表格"}],
                                 "rules":[{"dimension":"ROLE","value":"TEACHER"}],
                                 "memberProfileIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points").value(30))
                .andExpect(jsonPath("$.data.title").value("整理仿真数据（改）"))
                .andExpect(jsonPath("$.data.rules[*].value", hasItem("MEMBER")));

        // 已发布任务不能再发布，也不能删除。
        mvc.perform(post("/api/v1/admin/tasks/{id}/publish", taskId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/admin/tasks/{id}", taskId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isConflict());

        // 成员端能看到「我的任务」并提交。
        String myTasks = mvc.perform(get("/api/v1/tasks").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> assignmentIds = JsonPath.read(myTasks, "$.data[?(@.taskId=='" + taskId + "')].assignmentId");
        org.assertj.core.api.Assertions.assertThat(assignmentIds).isNotEmpty();
        String assignmentId = assignmentIds.getFirst();

        String detail = mvc.perform(get("/api/v1/tasks/{id}", assignmentId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.subtasks.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        List<String> subtaskIds = JsonPath.read(detail, "$.data.subtasks[*].id");

        mvc.perform(patch("/api/v1/tasks/{id}/subtasks/{subtaskId}", assignmentId, subtaskIds.getFirst())
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtasks[0].completed").value(true));

        // 未提交时管理员不能确认通过。
        mvc.perform(put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review", taskId, assignmentId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isConflict());

        // 其他成员不能读取别人的任务对象。
        String coreToken = login("core", "YesLab-Core-2026!");
        mvc.perform(get("/api/v1/tasks/{id}", assignmentId).header("Authorization", bearer(coreToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void approvalGrantsPointsAndIsIdempotentWhileRejectionSkipsThem() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");

        int before = memberPoints(memberToken);
        String taskId = createAndPublish(teacherToken, "积分任务", 25);
        String assignmentId = assignmentFor(teacherToken, taskId, "S-001");

        mvc.perform(post("/api/v1/tasks/{id}/submission", assignmentId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"已完成\"}"))
                .andExpect(status().isOk());

        // 重复审核不重复计分：第二次调用直接返回冲突，积分只增加一次。
        mvc.perform(put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review", taskId, assignmentId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED","comment":"验收通过","evidenceUrl":"https://example.com/pull/1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.awardedPoints").value(25));
        mvc.perform(put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review", taskId, assignmentId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(memberPoints(memberToken)).isEqualTo(before + 25);

        // 完成情况里能看到计分结果与子任务完成率。
        mvc.perform(get("/api/v1/admin/tasks/{id}/progress", taskId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvedCount").value(1))
                .andExpect(jsonPath("$.data.awardedPointsTotal").value(25))
                .andExpect(jsonPath("$.data.assignments[*].awardedPoints", hasItem(25)))
                .andExpect(jsonPath("$.data.subtaskProgress.length()").value(2));

        // 驳回必须填写意见，且不产生积分。
        int beforeReject = memberPoints(memberToken);
        String rejectTask = createAndPublish(teacherToken, "驳回任务", 40);
        String rejectAssignment = assignmentFor(teacherToken, rejectTask, "S-001");
        mvc.perform(post("/api/v1/tasks/{id}/submission", rejectAssignment)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"先交一版\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review",
                        rejectTask, rejectAssignment)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review",
                        rejectTask, rejectAssignment)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"comment\":\"请补充数据来源\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.awardedPoints").doesNotExist());
        org.assertj.core.api.Assertions.assertThat(memberPoints(memberToken)).isEqualTo(beforeReject);

        // 驳回后可以重新提交
        mvc.perform(post("/api/v1/tasks/{id}/submission", rejectAssignment)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"已补充数据来源\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    void ineligibleRecipientsAreSkippedWithReasonAndZeroPointTasksGrantNothing() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String coreToken = login("core", "YesLab-Core-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");

        // 教师对象不能计分。
        String teacherTask = createAndPublishWithRules(teacherToken, "教师任务", 20,
                "[{\"dimension\":\"ROLE\",\"value\":\"TEACHER\"}]");
        String teacherAssignment = assignmentFor(teacherToken, teacherTask, "T-001");
        String teacherUserToken = login("teacher", "YesLab-Teacher-2026!");
        mvc.perform(post("/api/v1/tasks/{id}/submission", teacherAssignment)
                        .header("Authorization", bearer(teacherUserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"已完成\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review",
                        teacherTask, teacherAssignment)
                        .header("Authorization", bearer(coreToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.pointsSkippedReason").value("指导教师不参与成员积分统计"));

        // 审核人给自己审核同样跳过。
        String coreTask = createAndPublishWithRules(teacherToken, "核心学生任务", 15,
                "[{\"dimension\":\"ROLE\",\"value\":\"CORE_STUDENT\"}]");
        String coreAssignment = assignmentFor(teacherToken, coreTask, "S-CORE-001");
        mvc.perform(post("/api/v1/tasks/{id}/submission", coreAssignment)
                        .header("Authorization", bearer(coreToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"已完成\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review",
                        coreTask, coreAssignment)
                        .header("Authorization", bearer(coreToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsSkippedReason").value("积分管理员不能给自己发放积分"));

        // 非正式成员不能计分：把 member 临时改为试用后审核。
        String memberProfileId = memberProfileId(teacherToken, "S-001");
        setMemberStatus(teacherToken, memberProfileId, "TRIAL");
        try {
            String trialTask = createAndPublishWithRules(teacherToken, "试用成员任务", 12,
                    "[{\"dimension\":\"ROLE\",\"value\":\"MEMBER\"},{\"dimension\":\"MEMBER_STATUS\",\"value\":\"TRIAL\"}]");
            String trialAssignment = assignmentFor(teacherToken, trialTask, "S-001");
            mvc.perform(post("/api/v1/tasks/{id}/submission", trialAssignment)
                            .header("Authorization", bearer(memberToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"completionNote\":\"已完成\"}"))
                    .andExpect(status().isOk());
            mvc.perform(put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review",
                            trialTask, trialAssignment)
                            .header("Authorization", bearer(teacherToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"APPROVED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pointsSkippedReason").value("只能给正式成员发放积分"));
        } finally {
            setMemberStatus(teacherToken, memberProfileId, "OFFICIAL");
        }

        // 积分值为 0 的任务不产生任何积分记录。
        int before = memberPoints(memberToken);
        String zeroTask = createAndPublish(teacherToken, "不计分任务", 0);
        String zeroAssignment = assignmentFor(teacherToken, zeroTask, "S-001");
        mvc.perform(post("/api/v1/tasks/{id}/submission", zeroAssignment)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"已完成\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review",
                        zeroTask, zeroAssignment)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.awardedPoints").doesNotExist());
        org.assertj.core.api.Assertions.assertThat(memberPoints(memberToken)).isEqualTo(before);
    }

    @Test
    void closedTaskBlocksMemberSubmissionButStillAllowsReview() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");
        String taskId = createAndPublish(teacherToken, "结项任务", 10);
        String assignmentId = assignmentFor(teacherToken, taskId, "S-001");

        mvc.perform(post("/api/v1/tasks/{id}/submission", assignmentId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNote\":\"已完成\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/admin/tasks/{id}/close", taskId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // 结束后成员不能修改
        mvc.perform(patch("/api/v1/tasks/{id}/subtasks/{subtaskId}", assignmentId, java.util.UUID.randomUUID())
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isConflict());

        // 但管理员仍可完成审核
        mvc.perform(put("/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review", taskId, assignmentId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.awardedPoints").value(10));

        // 富文本清洗：脚本与事件属性被移除，图片与链接保留。
        mvc.perform(post("/api/v1/admin/tasks")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"富文本任务",
                                 "contentHtml":"<p onclick=\\"evil()\\">安全内容</p><script>alert(1)</script><a href=\\"https://example.com\\">链接</a><img src=\\"https://example.com/a.png\\" onerror=\\"evil()\\"/>",
                                 "points":0,"subtasks":[{"title":"a"}],"rules":[{"dimension":"ROLE","value":"MEMBER"}],
                                 "memberProfileIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentHtml", not(org.hamcrest.Matchers.containsString("<script"))))
                .andExpect(jsonPath("$.data.contentHtml", not(org.hamcrest.Matchers.containsString("onclick"))))
                .andExpect(jsonPath("$.data.contentHtml", not(org.hamcrest.Matchers.containsString("onerror"))))
                .andExpect(jsonPath("$.data.contentHtml", org.hamcrest.Matchers.containsString("<img")))
                .andExpect(jsonPath("$.data.contentHtml", org.hamcrest.Matchers.containsString("example.com")));
    }

    @Test
    void subtaskDetailCarriesRichTextAndIsLazyLoaded() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");

        String created = mvc.perform(post("/api/v1/admin/tasks")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"子任务正文任务","contentHtml":"<p>任务正文</p>","points":0,
                                 "subtasks":[{"title":"第一步","contentHtml":"<p>先安装依赖 <code>mvn -v</code></p>"},
                                             {"title":"第二步"}],
                                 "rules":[{"dimension":"ROLE","value":"MEMBER"}],"memberProfileIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtasks[0].hasContent").value(true))
                .andExpect(jsonPath("$.data.subtasks[1].hasContent").value(false))
                .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(created, "$.data.id");
        mvc.perform(post("/api/v1/admin/tasks/{id}/publish", taskId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());

        String assignmentId = assignmentFor(teacherToken, taskId, "S-001");
        String detail = mvc.perform(get("/api/v1/tasks/{id}", assignmentId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                // 列表接口不带子任务正文，只给 hasContent。
                .andExpect(jsonPath("$.data.subtasks[0].hasContent").value(true))
                .andExpect(jsonPath("$.data.subtasks[0].contentHtml").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        List<String> subtaskIds = JsonPath.read(detail, "$.data.subtasks[*].id");

        // 子任务详情返回正文、本人完成状态与大任务上下文。
        mvc.perform(get("/api/v1/tasks/{assignmentId}/subtasks/{subtaskId}", assignmentId, subtaskIds.getFirst())
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("第一步"))
                .andExpect(jsonPath("$.data.contentHtml", org.hamcrest.Matchers.containsString("先安装依赖")))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.taskTitle").value("子任务正文任务"))
                .andExpect(jsonPath("$.data.completedSubtasks").value(0))
                .andExpect(jsonPath("$.data.totalSubtasks").value(2))
                .andExpect(jsonPath("$.data.editable").value(true));

        // 他人的对象不可读（核心学生不是该任务对象）。
        String coreToken = login("core", "YesLab-Core-2026!");
        mvc.perform(get("/api/v1/tasks/{assignmentId}/subtasks/{subtaskId}", assignmentId, subtaskIds.getFirst())
                        .header("Authorization", bearer(coreToken)))
                .andExpect(status().isNotFound());

        // 管理端可以按需读取子任务正文用于编辑。
        mvc.perform(get("/api/v1/admin/tasks/{taskId}/subtasks/{subtaskId}", taskId, subtaskIds.getFirst())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentHtml", org.hamcrest.Matchers.containsString("先安装依赖")));
    }

    @Test
    void publishedSubtaskSyncIsByIdAndKeepsProgress() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");

        String taskId = createAndPublish(teacherToken, "子任务改标题任务", 0);
        String assignmentId = assignmentFor(teacherToken, taskId, "S-001");
        String detail = mvc.perform(get("/api/v1/tasks/{id}", assignmentId)
                        .header("Authorization", bearer(memberToken)))
                .andReturn().getResponse().getContentAsString();
        List<String> subtaskIds = JsonPath.read(detail, "$.data.subtasks[*].id");
        String firstId = subtaskIds.getFirst();
        String secondId = subtaskIds.get(1);

        // 成员勾选第一项。
        mvc.perform(patch("/api/v1/tasks/{id}/subtasks/{subtaskId}", assignmentId, firstId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtasks[0].completed").value(true));

        // 管理员改标题（同一个 id）并补上正文：勾选记录必须原样保留。
        mvc.perform(put("/api/v1/admin/tasks/{id}", taskId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"子任务改标题任务","contentHtml":"<p>任务正文</p>","points":0,
                                 "subtasks":[{"id":"%s","title":"第一步（改名后）","contentHtml":"<p>新的说明</p>"},
                                             {"id":"%s","title":"第二步"}],
                                 "rules":[],"memberProfileIds":[]}
                                """).formatted(firstId, secondId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtasks[0].title").value("第一步（改名后）"))
                .andExpect(jsonPath("$.data.subtasks[0].hasContent").value(true));

        mvc.perform(get("/api/v1/tasks/{id}", assignmentId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtasks[0].completed").value(true))
                .andExpect(jsonPath("$.data.subtasks[0].title").value("第一步（改名后）"))
                .andExpect(jsonPath("$.data.subtasks[0].completed").value(true));

        mvc.perform(get("/api/v1/tasks/{assignmentId}/subtasks/{subtaskId}", assignmentId, firstId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(jsonPath("$.data.title").value("第一步（改名后）"))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.contentHtml", org.hamcrest.Matchers.containsString("新的说明")));

        // 删除第二项（提交里不再出现它的 id）：勾选记录一并清理。
        mvc.perform(put("/api/v1/admin/tasks/{id}", taskId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"子任务改标题任务","contentHtml":"<p>任务正文</p>","points":0,
                                 "subtasks":[{"id":"%s","title":"第一步（改名后）","contentHtml":"<p>新的说明</p>"}],
                                 "rules":[],"memberProfileIds":[]}
                                """).formatted(firstId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtasks.length()").value(1));
        mvc.perform(get("/api/v1/tasks/{id}", assignmentId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(jsonPath("$.data.subtasks.length()").value(1))
                .andExpect(jsonPath("$.data.subtasks[0].completed").value(true));

        // 提交里带上别的任务的子任务 id 会被拒绝。
        String otherTaskId = createAndPublish(teacherToken, "另一个任务", 0);
        String otherDetail = mvc.perform(get("/api/v1/admin/tasks/{id}", otherTaskId)
                        .header("Authorization", bearer(teacherToken)))
                .andReturn().getResponse().getContentAsString();
        String foreignId = JsonPath.read(otherDetail, "$.data.subtasks[0].id");
        mvc.perform(put("/api/v1/admin/tasks/{id}", taskId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"子任务改标题任务","contentHtml":"<p>任务正文</p>","points":0,
                                 "subtasks":[{"id":"%s","title":"第一步（改名后）"}],
                                 "rules":[],"memberProfileIds":[]}
                                """).formatted(foreignId)))
                .andExpect(status().isBadRequest());
    }

    // ---------- 辅助 ----------

    private String createAndPublish(String token, String title, int points) throws Exception {
        return createAndPublishWithRules(token, title, points, "[{\"dimension\":\"ROLE\",\"value\":\"MEMBER\"}]");
    }

    private String createAndPublishWithRules(String token, String title, int points, String rules) throws Exception {
        String created = mvc.perform(post("/api/v1/admin/tasks")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"title":"%s","contentHtml":"<p>任务正文</p>","startDate":"2026-09-01",
                                 "endDate":"2026-12-31","points":%d,
                                 "subtasks":[{"title":"第一步"},{"title":"第二步"}],"rules":%s,"memberProfileIds":[]}
                                """).formatted(title, points, rules)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(created, "$.data.id");
        mvc.perform(post("/api/v1/admin/tasks/{id}/publish", taskId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        return taskId;
    }

    private String assignmentFor(String token, String taskId, String memberCode) throws Exception {
        String progress = mvc.perform(get("/api/v1/admin/tasks/{id}/progress", taskId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(progress,
                "$.data.assignments[?(@.memberCode=='" + memberCode + "')].assignmentId");
        org.assertj.core.api.Assertions.assertThat(ids).as("成员 %s 的任务对象", memberCode).isNotEmpty();
        return ids.getFirst();
    }

    private int memberPoints(String memberToken) throws Exception {
        String summary = mvc.perform(get("/api/v1/member/points").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(summary, "$.data.totalPoints");
    }

    private String memberProfileId(String token, String memberCode) throws Exception {
        String members = mvc.perform(get("/api/v1/admin/members").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(members, "$.data[?(@.memberCode=='" + memberCode + "')].id");
        org.assertj.core.api.Assertions.assertThat(ids).isNotEmpty();
        return ids.getFirst();
    }

    private void setMemberStatus(String token, String profileId, String status) throws Exception {
        mvc.perform(put("/api/v1/admin/members/{id}", profileId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"name":"范桌轩大王","memberCode":"S-001","role":"MEMBER",
                                 "major":"人工智能","className":"人工智能 2401","grade":"2024",
                                 "internalContact":"member@yes-lab.internal","status":"%s",
                                 "skillTags":["具身智能"]}
                                """).formatted(status)))
                .andExpect(status().isOk());
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

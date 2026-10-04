package cn.yeslab.platform.deadline.service;

import cn.yeslab.platform.identity.model.*;
import cn.yeslab.platform.identity.repository.*;
import cn.yeslab.platform.task.model.*;
import cn.yeslab.platform.task.repository.*;
import cn.yeslab.platform.recruitment.model.RecruitmentApplicationEntity;
import cn.yeslab.platform.recruitment.repository.RecruitmentApplicationRepository;
import cn.yeslab.platform.project.model.*;
import cn.yeslab.platform.project.repository.ProjectTeamRepository;
import cn.yeslab.platform.achievement.model.*;
import cn.yeslab.platform.achievement.repository.CompetitionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class DeadlineApiTests {
    @Autowired WebApplicationContext context;
    @Autowired AccountRepository accounts;
    @Autowired MemberProfileRepository profiles;
    @Autowired TaskRepository tasks;
    @Autowired TaskAssignmentRepository assignments;
    @Autowired RecruitmentApplicationRepository applications;
    @Autowired ProjectTeamRepository projects;
    @Autowired CompetitionRepository competitions;
    MockMvc mvc; MemberProfileEntity member, teacher; String token;
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        member = profiles.findAll().stream().filter(p -> p.getAccount().getUsername().equals("member")).findFirst().orElseThrow();
        teacher = profiles.findAll().stream().filter(p -> p.getAccount().getUsername().equals("teacher")).findFirst().orElseThrow();
        token = JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"member\",\"password\":\"YesLab-Member-2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.accessToken");
    }
    TaskEntity task(String name, TaskType kind, TaskStatus state, LocalDate end) {
        return tasks.saveAndFlush(new TaskEntity(kind, name, "<p>公开名称之外的任务内容</p>", today.minusDays(2), end, 0, state, teacher.getAccount()));
    }
    String publicRows(String query) throws Exception {
        return mvc.perform(get("/api/v1/public/deadlines" + query)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    String ownRows() throws Exception {
        return mvc.perform(get("/api/v1/me/deadlines?pageSize=50").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    List<String> titles(String body) { return JsonPath.read(body, "$.data.entries[*].title"); }
    @Test void homepageWindowFiltersBeforePaginationAndPreservesPersonalHistory() throws Exception {
        var old = task("逾期十五天", TaskType.BOUNTY, TaskStatus.PUBLISHED, today.minusDays(15));
        assignments.saveAndFlush(new TaskAssignmentEntity(old, member, null, TaskAssignmentSource.MANUAL));
        task("逾期十四天", TaskType.BOUNTY, TaskStatus.PUBLISHED, today.minusDays(14));
        task("逾期十三天", TaskType.BOUNTY, TaskStatus.PUBLISHED, today.minusDays(13));
        task("下一笔悬赏", TaskType.BOUNTY, TaskStatus.PUBLISHED, today.plusDays(2));
        String first = publicRows("?type=BOUNTY&homepageWindow=true&pageSize=2");
        String second = publicRows("?type=BOUNTY&homepageWindow=true&pageSize=2&page=1");
        assertThat(JsonPath.<Integer>read(first, "$.data.totalCount")).isEqualTo(3);
        assertThat(titles(first)).containsExactly("下一笔悬赏", "逾期十三天");
        assertThat(titles(second)).containsExactly("逾期十四天");
        assertThat(titles(publicRows("?type=BOUNTY&pageSize=50"))).contains("逾期十五天");
        assertThat(titles(ownRows())).contains("逾期十五天");
    }
    @Test void homepageWindowUsesBeijingCalendarAndExactInstantBoundary() {
        Instant now = Instant.parse("2026-10-03T16:00:00Z"); // Beijing October 4 midnight
        assertThat(DeadlineService.withinHomepageWindow(LocalDate.of(2026, 9, 20), null, now)).isTrue();
        assertThat(DeadlineService.withinHomepageWindow(LocalDate.of(2026, 9, 19), null, now)).isFalse();
        assertThat(DeadlineService.withinHomepageWindow(null, now.minus(Duration.ofDays(14)), now)).isTrue();
        assertThat(DeadlineService.withinHomepageWindow(null, now.minus(Duration.ofDays(14)).minusNanos(1), now)).isFalse();
        assertThat(DeadlineService.withinHomepageWindow(LocalDate.of(2026, 9, 19), null, now.minusNanos(1))).isTrue();
    }
    @Test void publicIncludesUnassignedTasksAndPrivateProjectNamesButNoPrivateContents() throws Exception {
        var mine = task("我的普通任务", TaskType.STANDARD, TaskStatus.PUBLISHED, today.plusDays(2));
        assignments.save(new TaskAssignmentEntity(mine, member, null, TaskAssignmentSource.MANUAL));
        task("所有人的悬赏", TaskType.BOUNTY, TaskStatus.PUBLISHED, today.plusDays(1));
        task("任务草稿", TaskType.STANDARD, TaskStatus.DRAFT, today.plusDays(1));
        task("没有期限", TaskType.STANDARD, TaskStatus.PUBLISHED, null);
        var privateProject = new ProjectTeamEntity("非公开项目倒计时", "团队", "内部说明", ProjectType.RESEARCH, ProjectStatus.ACTIVE, teacher, null, teacher.getAccount());
        privateProject.updateDetails("非公开项目倒计时", "内部说明", ProjectType.RESEARCH, ProjectStatus.ACTIVE, null, List.of(), today, today.plusDays(3), List.of(), "", "", "", "", false);
        projects.saveAndFlush(privateProject);
        String pub = publicRows("?pageSize=50");
        assertThat(titles(pub)).contains("我的普通任务", "所有人的悬赏", "非公开项目倒计时").doesNotContain("任务草稿", "没有期限");
        assertThat(pub).doesNotContain("内部说明", "公开名称之外的任务内容", "memberCode", "registration", "applicant", member.getAccount().getId().toString());
        assertThat(titles(ownRows())).contains("我的普通任务").doesNotContain("所有人的悬赏", "非公开项目倒计时");
    }
    @Test void competitionPhasesDeduplicateDatesAndFinishedResultsHaveNoInventedDeadline() throws Exception {
        var c = new CompetitionEntity("省国赛日程", CompetitionLevel.NATIONAL, CompetitionLifecycle.PLANNED, "说明", member, member.getAccount());
        c.updateDetails(c.getName(), "", c.getLevel(), c.getLifecycle(), "", "说明", today.plusDays(4), today.minusDays(1), today.plusDays(4), null, "", null);
        competitions.saveAndFlush(c);
        var finished = new CompetitionEntity("已完赛等待成绩", CompetitionLevel.SCHOOL, CompetitionLifecycle.FINISHED, "说明", member, member.getAccount());
        finished.updateDetails(finished.getName(), "", finished.getLevel(), finished.getLifecycle(), "", "说明", today.plusDays(4), null, null, null, "", null);
        finished.setResultStatus(CompetitionResultStatus.PENDING_RESULT); competitions.saveAndFlush(finished);
        String pub = publicRows("?type=COMPETITION&pageSize=50");
        assertThat(titles(pub)).containsOnlyOnce("省国赛日程").doesNotContain("已完赛等待成绩");
        assertThat(titles(ownRows())).contains("省国赛日程").doesNotContain("已完赛等待成绩");
        assertThat(JsonPath.<List<String>>read(pub, "$.data.entries[?(@.title == '省国赛日程')].milestone")).containsExactly("国赛时间");
    }
    @Test void personalUsesExtensionAndExactResubmissionButPublicAggregatesWithoutNames() throws Exception {
        var newbie = task("共享新手任务", TaskType.ONBOARDING, TaskStatus.PUBLISHED, null);
        var application = applications.saveAndFlush(new RecruitmentApplicationEntity(member.getAccount(), "不可公开的报名姓名", "专业", "班级", "2026", "联系方式", List.of(), List.of(), "", List.of()));
        var assignment = new TaskAssignmentEntity(newbie, null, application, TaskAssignmentSource.ONBOARDING);
        assignment.extendDueDate(today.plusDays(7), teacher.getAccount(), "个人延期"); assignments.saveAndFlush(assignment);
        String publicBody = publicRows("?type=ONBOARDING&pageSize=50");
        assertThat(publicBody).contains(today.plusDays(7).toString()).doesNotContain("不可公开的报名姓名", application.getId().toString(), assignment.getId().toString(), "个人延期");
        assertThat(ownRows()).contains(today.plusDays(7).toString());
        Instant reviewTime = Instant.now().minusSeconds(600); assignment.reject(teacher.getAccount(), "修改说明", reviewTime); assignments.flush();
        String own = ownRows();
        assertThat(own).contains("重交截止", reviewTime.plusSeconds(86400).toString());
        assignment.approve(teacher.getAccount(), "完成"); assignments.flush();
        assertThat(titles(ownRows())).doesNotContain("共享新手任务");
    }
    @Test void filtersPagingAndFreshRoleGuard() throws Exception {
        for (int i = 0; i < 4; i++) task("分页任务" + i, TaskType.STANDARD, TaskStatus.PUBLISHED, today.plusDays(i + 1));
        String first = publicRows("?type=STANDARD&pageSize=2");
        String second = publicRows("?type=STANDARD&pageSize=2&page=1");
        assertThat(titles(first)).doesNotContainAnyElementsOf(titles(second));
        assertThat(JsonPath.<List<String>>read(first, "$.data.entries[*].sourceType")).containsOnly("STANDARD");
        mvc.perform(get("/api/v1/public/deadlines?pageSize=51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/me/deadlines")).andExpect(status().isUnauthorized());
        member.getAccount().setRole(Role.VISITOR); accounts.flush();
        mvc.perform(get("/api/v1/me/deadlines").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }
    @Test void datesUseBeijingAndInstantDeadlinesExpireExactlyAtBoundary() {
        Instant midnight = Instant.parse("2026-10-03T16:00:00Z");
        assertThat(DeadlineService.deadlineStatus(LocalDate.of(2026, 10, 4), null, midnight.minusMillis(1))).isEqualTo("UPCOMING");
        assertThat(DeadlineService.deadlineStatus(LocalDate.of(2026, 10, 4), null, midnight)).isEqualTo("TODAY");
        assertThat(DeadlineService.deadlineStatus(LocalDate.of(2026, 10, 3), null, midnight)).isEqualTo("OVERDUE");
        assertThat(DeadlineService.deadlineStatus(null, midnight, midnight.minusMillis(1))).isEqualTo("UPCOMING");
        assertThat(DeadlineService.deadlineStatus(null, midnight, midnight)).isEqualTo("OVERDUE");
    }

    @Test void publicParticipantsUseIdentityAndExcludeWithdrawnOrRejectedBountyClaims() throws Exception {
        teacher.updateManagedFields(member.getName(), teacher.getMemberCode(), "专业", "班级", "2026", "不公开联系方式", MemberStatus.OFFICIAL, List.of());
        profiles.flush();
        var ordinary = task("同名成员任务", TaskType.STANDARD, TaskStatus.PUBLISHED, today.plusDays(1));
        assignments.save(new TaskAssignmentEntity(ordinary, member, null, TaskAssignmentSource.MANUAL));
        assignments.save(new TaskAssignmentEntity(ordinary, teacher, null, TaskAssignmentSource.MANUAL));
        var bounty = task("退出悬赏", TaskType.BOUNTY, TaskStatus.PUBLISHED, today.plusDays(2));
        var abandoned = new TaskAssignmentEntity(bounty, member, null, TaskAssignmentSource.MANUAL);
        abandoned.abandon(); assignments.save(abandoned);
        var rejected = new TaskAssignmentEntity(bounty, teacher, null, TaskAssignmentSource.MANUAL);
        rejected.revokeCompletion(teacher.getAccount(), "撤销"); assignments.saveAndFlush(rejected);
        String pub = publicRows("?pageSize=50");
        assertThat(JsonPath.<List<Integer>>read(pub, "$.data.entries[?(@.title == '同名成员任务')].participantCount")).containsExactly(2);
        assertThat(JsonPath.<List<String>>read(pub, "$.data.entries[?(@.title == '同名成员任务')].participants[*].name")).containsExactly(member.getName(), member.getName());
        assertThat(JsonPath.<List<Integer>>read(pub, "$.data.entries[?(@.title == '退出悬赏')].participantCount")).containsExactly(0);
        assertThat(pub).doesNotContain("不公开联系方式", teacher.getMemberCode(), member.getId().toString(), teacher.getId().toString());
    }

    @Test void publicTeamRolesMergeByIdentityAndNewbieNamesRemainPrivate() throws Exception {
        var p = new ProjectTeamEntity("真实项目成员", "团队", "私有内容", ProjectType.RESEARCH, ProjectStatus.ACTIVE, member, teacher, teacher.getAccount());
        p.updateDetails(p.getProjectName(), "私有内容", p.getType(), p.getStatus(), teacher, List.of(), today, today.plusDays(2), List.of(), "", "", "", "", false);
        p.updateTeam("团队", member, Set.of(member), Set.of(teacher)); projects.saveAndFlush(p);
        var c = new CompetitionEntity("真实参赛成员", CompetitionLevel.NATIONAL, CompetitionLifecycle.PLANNED, "说明", member, member.getAccount());
        c.updateDetails(c.getName(), "", c.getLevel(), c.getLifecycle(), "", "说明", today.plusDays(4), null, null, teacher, "导师", null);
        c.replaceParticipants(List.of(new CompetitionParticipantEntity("旧名字", member, true, 0), new CompetitionParticipantEntity("历史手填姓名", null, false, 1)));
        competitions.saveAndFlush(c);
        var newbie = task("人数保护新手", TaskType.ONBOARDING, TaskStatus.PUBLISHED, today.plusDays(3));
        var application = applications.saveAndFlush(new RecruitmentApplicationEntity(member.getAccount(), "隐藏申请者姓名", "专业", "班级", "2026", "联系方式", List.of(), List.of(), "", List.of()));
        assignments.saveAndFlush(new TaskAssignmentEntity(newbie, null, application, TaskAssignmentSource.ONBOARDING));
        String pub = publicRows("?pageSize=50");
        assertThat(JsonPath.<List<Integer>>read(pub, "$.data.entries[?(@.title == '真实项目成员')].participantCount")).containsExactly(2);
        assertThat(JsonPath.<List<String>>read(pub, "$.data.entries[?(@.title == '真实项目成员')].participants[*].role")).containsExactly("负责人 / 成员", "导师");
        assertThat(JsonPath.<List<Integer>>read(pub, "$.data.entries[?(@.title == '真实参赛成员')].participantCount")).containsExactly(3);
        assertThat(pub).contains("历史手填姓名").doesNotContain("旧名字", "隐藏申请者姓名", "私有内容");
        assertThat(JsonPath.<List<Integer>>read(pub, "$.data.entries[?(@.title == '人数保护新手')].participantCount")).containsExactly(1);
        assertThat(JsonPath.<List<String>>read(pub, "$.data.entries[?(@.title == '人数保护新手')].participantVisibility")).containsExactly("AGGREGATE_ONLY");
    }
}

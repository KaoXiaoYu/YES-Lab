package cn.yeslab.platform.deadline.service;

import cn.yeslab.platform.deadline.api.DeadlineModels;
import cn.yeslab.platform.deadline.api.DeadlineModels.*;
import cn.yeslab.platform.identity.model.*;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.task.model.*;
import cn.yeslab.platform.task.repository.*;
import cn.yeslab.platform.project.model.*;
import cn.yeslab.platform.project.repository.ProjectTeamRepository;
import cn.yeslab.platform.achievement.model.*;
import cn.yeslab.platform.achievement.repository.CompetitionRepository;
import cn.yeslab.platform.common.error.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadlineService {
    private static final ZoneId LAB = ZoneId.of("Asia/Shanghai");
    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final ProjectTeamRepository projects;
    private final CompetitionRepository competitions;
    private final MemberProfileRepository profiles;
    private final AuthService auth;
    public DeadlineService(TaskRepository tasks, TaskAssignmentRepository assignments, ProjectTeamRepository projects,
            CompetitionRepository competitions, MemberProfileRepository profiles, AuthService auth) {
        this.tasks = tasks; this.assignments = assignments; this.projects = projects;
        this.competitions = competitions; this.profiles = profiles; this.auth = auth;
    }
    @Transactional(readOnly = true)
    public DeadlinePage publicDeadlines(SourceType type, int page, int size, boolean homepageWindow) {
        Instant now = Instant.now(); LocalDate today = now.atZone(LAB).toLocalDate();
        var result = new ArrayList<Entry>();
        var onboard = new HashMap<UUID, LocalDate>();
        var onboardCounts = new HashMap<UUID, Long>();
        var taskMembers = new HashMap<UUID, LinkedHashMap<UUID, Participant>>();
        if (type == null || type == SourceType.STANDARD || type == SourceType.BOUNTY) {
            for (var participant : assignments.findDeadlineParticipants(TaskStatus.PUBLISHED, TaskType.ONBOARDING)) {
                if (participant.getStatus() == TaskAssignmentStatus.ABANDONED
                        || participant.getTaskType() == TaskType.BOUNTY && participant.getStatus() == TaskAssignmentStatus.REJECTED) continue;
                taskMembers.computeIfAbsent(participant.getTaskId(), ignored -> new LinkedHashMap<>())
                        .putIfAbsent(participant.getProfileId(), new Participant(participant.getName(), participant.getTaskType() == TaskType.BOUNTY ? "接取成员" : "参与成员"));
            }
        }
        if (type == null || type == SourceType.ONBOARDING) {
            for (var group : assignments.aggregateOnboardingDeadlines(today, TaskType.ONBOARDING, TaskStatus.PUBLISHED,
                    List.of(TaskAssignmentStatus.APPROVED, TaskAssignmentStatus.ABANDONED))) {
                var date = group.getUpcomingDeadline() != null ? group.getUpcomingDeadline() : group.getLatestDeadline();
                if (date != null) onboard.put(group.getTaskId(), date);
                onboardCounts.put(group.getTaskId(), group.getParticipantCount());
            }
        }
        for (var task : tasks.findByStatus(TaskStatus.PUBLISHED)) {
            SourceType kind = SourceType.valueOf(task.getTaskType().name());
            if (type != null && type != kind) continue;
            var deadline = task.isOnboarding() ? onboard.get(task.getId()) : task.getEndDate();
            add(result, kind, task.getId(), "deadline", task.getTitle(), task.isOnboarding() ? "最近一批截止" : "截止时间",
                    deadline, null, task.isBounty() ? "/bounties/" + task.getId() : "/tasks", now,
                    task.isOnboarding() ? new Participants(List.of(), onboardCounts.getOrDefault(task.getId(), 0L), "AGGREGATE_ONLY")
                            : named(taskMembers.getOrDefault(task.getId(), new LinkedHashMap<>()).values()));
        }
        addProjectsAndCompetitions(result, null, type, now);
        if (homepageWindow) result.removeIf(entry -> !withinHomepageWindow(entry.deadlineDate(), entry.deadlineAt(), now));
        return page(result, type, page, size, now);
    }
    @Transactional(readOnly = true)
    public DeadlinePage ownDeadlines(Authentication authentication, SourceType type, int page, int size) {
        var account = auth.requireAccount(authentication);
        if (account.getRole() == Role.VISITOR) throw new ApiException(HttpStatus.FORBIDDEN, "个人倒计时仅对成员开放");
        var profile = profiles.findByAccountId(account.getId()).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "请先建立成员档案"));
        Instant now = Instant.now(); var result = new ArrayList<Entry>();
        for (var assignment : assignments.findRelatedAssignments(profile.getId(), account.getId())) {
            var task = assignment.getTask();
            if (task.getStatus() != TaskStatus.PUBLISHED || assignment.getStatus() == TaskAssignmentStatus.APPROVED
                    || assignment.getStatus() == TaskAssignmentStatus.ABANDONED) continue;
            SourceType kind = SourceType.valueOf(task.getTaskType().name());
            if (type != null && type != kind) continue;
            Instant precise = assignment.getStatus() == TaskAssignmentStatus.REJECTED && !task.isBounty()
                    ? assignment.getResubmissionDeadlineAt() : null;
            add(result, kind, task.getId(), "assignment-" + assignment.getId(), task.getTitle(), precise == null ? "截止时间" : "重交截止",
                    precise == null ? TaskTiming.deadlineOf(assignment) : null, precise,
                    task.isBounty() ? "/bounties/" + task.getId() : task.isOnboarding() ? "/application" : "/tasks/" + assignment.getId(), now);
        }
        addProjectsAndCompetitions(result, profile.getId(), type, now);
        return page(result, type, page, size, now);
    }
    private void addProjectsAndCompetitions(List<Entry> rows, UUID memberId, SourceType type, Instant now) {
        if (type == null || type == SourceType.PROJECT) for (var project : projects.findDeadlineProjects(List.of(ProjectStatus.PLANNING, ProjectStatus.ACTIVE))) {
            if (project.getStatus() != ProjectStatus.PLANNING && project.getStatus() != ProjectStatus.ACTIVE) continue;
            if (memberId != null && !relatedProject(project, memberId)) continue;
            add(rows, SourceType.PROJECT, project.getId(), "end", project.getProjectName(), "项目截止",
                    project.getEndDate(), null, "/projects/" + project.getId(), now, projectParticipants(project));
        }
        if (type == null || type == SourceType.COMPETITION) for (var competition : competitions.findDeadlineCompetitions(CompetitionLifecycle.FINISHED)) {
            if (competition.getLifecycle() == CompetitionLifecycle.FINISHED) continue;
            if (memberId != null && !relatedCompetition(competition, memberId)) continue;
            var dates = new LinkedHashMap<LocalDate, String>();
            if (competition.getProvincialDate() != null) dates.put(competition.getProvincialDate(), "省赛");
            if (competition.getNationalDate() != null) dates.put(competition.getNationalDate(), "国赛");
            if (competition.getCompetitionDate() != null) dates.putIfAbsent(competition.getCompetitionDate(), "比赛");
            LocalDate today = now.atZone(LAB).toLocalDate();
            boolean hasUpcoming = dates.keySet().stream().anyMatch(d -> !d.isBefore(today));
            LocalDate last = dates.keySet().stream().max(Comparator.naturalOrder()).orElse(null);
            var participants = competitionParticipants(competition);
            dates.forEach((date, stage) -> {
                if (hasUpcoming ? date.isBefore(today) : !date.equals(last)) return;
                add(rows, SourceType.COMPETITION, competition.getId(), stage, competition.getName(), stage + "时间", date, null,
                        "/competitions", now, participants);
            });
        }
    }
    private boolean relatedProject(ProjectTeamEntity p, UUID id) {
        return p.getLeader().getId().equals(id) || p.getAdvisor() != null && p.getAdvisor().getId().equals(id)
                || p.getMembers().stream().anyMatch(m -> m.getId().equals(id));
    }
    private boolean relatedCompetition(CompetitionEntity c, UUID id) {
        return c.getCaptainProfile().getId().equals(id) || c.getAdvisorProfile() != null && c.getAdvisorProfile().getId().equals(id)
                || c.getParticipants().stream().anyMatch(p -> p.getLinkedProfile() != null && p.getLinkedProfile().getId().equals(id));
    }
    static String deadlineStatus(LocalDate date, Instant at, Instant now) {
        if (at != null) return !now.isBefore(at) ? "OVERDUE" : "UPCOMING";
        LocalDate today = now.atZone(LAB).toLocalDate();
        return date.isBefore(today) ? "OVERDUE" : date.equals(today) ? "TODAY" : "UPCOMING";
    }
    private void add(List<Entry> rows, SourceType type, UUID id, String key, String title, String milestone,
            LocalDate date, Instant at, String href, Instant now) {
        add(rows, type, id, key, title, milestone, date, at, href, now, named(List.of()));
    }
    private record Participants(List<Participant> names, long count, String visibility) {}
    private Participants named(Collection<Participant> names) {
        return new Participants(List.copyOf(names), names.size(), "NAMES");
    }
    private void include(Map<UUID, Participant> names, MemberProfileEntity profile, String role) {
        if (profile == null) return;
        names.compute(profile.getId(), (id, previous) -> new Participant(profile.getName(),
                previous == null ? role : previous.role().equals(role) ? role : previous.role() + " / " + role));
    }
    private Participants projectParticipants(ProjectTeamEntity project) {
        var names = new LinkedHashMap<UUID, Participant>();
        include(names, project.getLeader(), "负责人");
        for (var member : project.getMembers()) include(names, member, "成员");
        include(names, project.getAdvisor(), "导师");
        return named(names.values());
    }
    private Participants competitionParticipants(CompetitionEntity competition) {
        var names = new LinkedHashMap<UUID, Participant>();
        include(names, competition.getCaptainProfile(), "队长");
        for (var participant : competition.getParticipants()) {
            if (participant.getLinkedProfile() != null) include(names, participant.getLinkedProfile(), participant.isCaptain() ? "队长" : "参赛成员");
        }
        include(names, competition.getAdvisorProfile(), "导师");
        var result = new ArrayList<>(names.values());
        for (var participant : competition.getParticipants()) {
            if (participant.getLinkedProfile() == null) result.add(new Participant(participant.getDisplayName(), "历史参赛姓名"));
        }
        return named(result);
    }
    private void add(List<Entry> rows, SourceType type, UUID id, String key, String title, String milestone,
            LocalDate date, Instant at, String href, Instant now, Participants participants) {
        if (date == null && at == null) return;
        rows.add(new Entry(type, id, key, title, milestone, date, at, deadlineStatus(date, at, now), href,
                participants.names(), participants.count(), participants.visibility()));
    }
    static boolean withinHomepageWindow(LocalDate date, Instant at, Instant now) {
        return at != null ? !at.isBefore(now.minus(Duration.ofDays(14)))
                : date != null && !date.isBefore(now.atZone(LAB).toLocalDate().minusDays(14));
    }
    private static Instant due(Entry entry) {
        return entry.deadlineAt() != null ? entry.deadlineAt() : entry.deadlineDate().atTime(LocalTime.MAX).atZone(LAB).toInstant();
    }
    private DeadlinePage page(List<Entry> all, SourceType type, int page, int size, Instant now) {
        if (page < 0 || page > 1000000 || size < 1 || size > 50) throw new ApiException(HttpStatus.BAD_REQUEST, "分页参数无效");
        var sorted = all.stream().filter(row -> type == null || row.sourceType() == type)
                .sorted(Comparator.comparing((Entry e) -> e.status().equals("OVERDUE"))
                        .thenComparing(e -> e.status().equals("OVERDUE") ? -due(e).toEpochMilli() : due(e).toEpochMilli())
                        .thenComparing(Entry::sourceType).thenComparing(Entry::sourceId).thenComparing(Entry::milestoneKey))
                .toList();
        return new DeadlinePage(sorted.stream().skip((long) page * size).limit(size).toList(), sorted.size(), page, size, now.atZone(LAB).toLocalDate(), now);
    }
}

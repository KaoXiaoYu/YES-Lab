package cn.yeslab.platform.recruitment.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.model.MemberStatus;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.AccountRepository;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.recruitment.api.RecruitmentModels;
import cn.yeslab.platform.recruitment.model.RecruitmentApplicationEntity;
import cn.yeslab.platform.recruitment.model.RecruitmentStage;
import cn.yeslab.platform.recruitment.model.RecruitmentStatusHistoryEntity;
import cn.yeslab.platform.recruitment.repository.RecruitmentApplicationRepository;
import cn.yeslab.platform.recruitment.repository.RecruitmentStatusHistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RecruitmentService {

    private static final Map<RecruitmentStage, EnumSet<RecruitmentStage>> ALLOWED_TRANSITIONS = transitions();

    private final RecruitmentApplicationRepository applications;
    private final RecruitmentStatusHistoryRepository histories;
    private final AccountRepository accounts;
    private final MemberProfileRepository profiles;
    private final AuthService authService;

    public RecruitmentService(
            RecruitmentApplicationRepository applications,
            RecruitmentStatusHistoryRepository histories,
            AccountRepository accounts,
            MemberProfileRepository profiles,
            AuthService authService
    ) {
        this.applications = applications;
        this.histories = histories;
        this.accounts = accounts;
        this.profiles = profiles;
        this.authService = authService;
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_VIEW')")
    @Transactional(readOnly = true)
    public RecruitmentModels.ApplicationView getOwn(Authentication authentication) {
        AccountEntity account = authService.requireAccount(authentication);
        return applications.findByApplicantId(account.getId()).map(this::toView).orElse(null);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_EDIT')")
    @Transactional
    public RecruitmentModels.ApplicationView saveOwn(
            Authentication authentication,
            RecruitmentModels.ApplicationRequest request
    ) {
        AccountEntity account = authService.requireAccount(authentication);
        RecruitmentApplicationEntity application = applications.findByApplicantId(account.getId()).orElse(null);
        if (application != null && application.getStage() != RecruitmentStage.SIGNUP) {
            throw new ApiException(HttpStatus.CONFLICT, "报名表进入初筛后不能自行修改，请联系管理员");
        }

        if (application == null) {
            application = applications.save(new RecruitmentApplicationEntity(
                    account, request.name().trim(), request.major().trim(), request.className().trim(), normalize(request.grade()),
                    request.contact().trim(), cleanList(request.interestDirections()), cleanList(request.existingSkills()),
                    normalize(request.experience()), cleanList(request.intendedTags())
            ));
            histories.save(new RecruitmentStatusHistoryEntity(
                    application.getId(), null, RecruitmentStage.SIGNUP, snapshot(account), "提交报名表"
            ));
        } else {
            application.updateApplication(
                    request.name().trim(), request.major().trim(), request.className().trim(), normalize(request.grade()),
                    request.contact().trim(), cleanList(request.interestDirections()), cleanList(request.existingSkills()),
                    normalize(request.experience()), cleanList(request.intendedTags())
            );
            application = applications.save(application);
        }
        return toView(application);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional(readOnly = true)
    public List<RecruitmentModels.ApplicationView> listApplications() {
        return applications.findAll().stream()
                .sorted((left, right) -> right.getUpdatedAt().compareTo(left.getUpdatedAt()))
                .map(this::toView)
                .toList();
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional(readOnly = true)
    public List<RecruitmentModels.InterviewerView> listInterviewers() {
        return accounts.findByRoleInAndEnabledTrue(List.of(Role.TEACHER, Role.CORE_STUDENT)).stream()
                .map(account -> profiles.findByAccountId(account.getId())
                        .map(profile -> new RecruitmentModels.InterviewerView(
                                account.getId(), account.getUsername(), profile.getName(), profile.getMemberCode(), account.getRole().name()
                        )).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional
    public RecruitmentModels.ApplicationView changeStage(
            Authentication authentication,
            UUID applicationId,
            RecruitmentModels.StageChangeRequest request
    ) {
        AccountEntity operator = authService.requireAccount(authentication);
        RecruitmentApplicationEntity application = requireApplication(applicationId);
        changeStage(application, request.stage(), operator, normalize(request.note()));
        if (request.stage() == RecruitmentStage.SKILL_TEST) {
            application.setLinkedQuizId(normalize(request.linkedQuizId()));
        }
        return toView(applications.save(application));
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional
    public RecruitmentModels.ApplicationView recordInterview(
            Authentication authentication,
            UUID applicationId,
            RecruitmentModels.InterviewRequest request
    ) {
        AccountEntity operator = authService.requireAccount(authentication);
        RecruitmentApplicationEntity application = requireApplication(applicationId);
        AccountEntity interviewer = accounts.findByUsernameIgnoreCase(request.interviewerUsername())
                .filter(AccountEntity::isEnabled)
                .filter(account -> account.getRole().isSystemAdmin())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "面试官必须是教师或核心学生"));

        if (application.getStage() == RecruitmentStage.SCREENING) {
            changeStage(application, RecruitmentStage.INTERVIEW, operator, "分配面试官");
        } else if (application.getStage() != RecruitmentStage.INTERVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "只有初筛或面试阶段可以记录面试信息");
        }

        application.recordInterview(
                interviewer,
                request.score(),
                normalize(request.evaluation()),
                cleanList(request.suggestedTags()),
                request.passed()
        );
        return toView(applications.save(application));
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE') and hasAuthority('MEMBER_MANAGE')")
    @Transactional
    public RecruitmentModels.ApplicationView convertToMember(
            Authentication authentication,
            UUID applicationId,
            RecruitmentModels.ConvertMemberRequest request
    ) {
        AccountEntity operator = authService.requireAccount(authentication);
        RecruitmentApplicationEntity application = requireApplication(applicationId);
        if (application.getStage() != RecruitmentStage.PROBATION) {
            throw new ApiException(HttpStatus.CONFLICT, "只有试用期人员可以转为正式成员");
        }
        if (profiles.existsByMemberCodeIgnoreCase(request.memberCode().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "学号或内部编号已存在");
        }
        if (profiles.findByAccountId(application.getApplicant().getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "该账号已经关联成员资料");
        }

        AccountEntity account = application.getApplicant();
        account.setRole(Role.MEMBER);
        accounts.save(account);
        MemberProfileEntity profile = profiles.save(new MemberProfileEntity(
                account,
                application.getName(),
                request.memberCode().trim(),
                application.getMajor(),
                application.getClassName(),
                application.getGrade(),
                application.getContact(),
                MemberStatus.OFFICIAL,
                cleanList(request.skillTags())
        ));
        application.markConverted(profile.getId());
        changeStage(application, RecruitmentStage.FORMAL_MEMBER, operator, "一键转换为正式成员账号");
        return toView(applications.save(application));
    }

    private void changeStage(
            RecruitmentApplicationEntity application,
            RecruitmentStage target,
            AccountEntity operator,
            String note
    ) {
        RecruitmentStage current = application.getStage();
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(RecruitmentStage.class)).contains(target)) {
            throw new ApiException(HttpStatus.CONFLICT, "不允许从 " + current + " 直接流转到 " + target);
        }
        application.changeStage(target);
        histories.save(new RecruitmentStatusHistoryEntity(application.getId(), current, target, snapshot(operator), note));
    }

    private RecruitmentApplicationEntity requireApplication(UUID applicationId) {
        return applications.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "报名记录不存在"));
    }

    private RecruitmentModels.ApplicationView toView(RecruitmentApplicationEntity application) {
        List<RecruitmentModels.HistoryView> history = histories.findByApplicationIdOrderByChangedAtAsc(application.getId()).stream()
                .map(item -> new RecruitmentModels.HistoryView(
                        item.getFromStage(), item.getToStage(), item.getOperatorUsername(), item.getNote(), item.getChangedAt()
                ))
                .toList();
        RecruitmentModels.InterviewView interview = new RecruitmentModels.InterviewView(
                application.getInterviewerAccountId(), application.getInterviewerName(), application.getInterviewScore(),
                application.getInterviewEvaluation(), application.getSuggestedTags(), application.getInterviewPassed()
        );
        return new RecruitmentModels.ApplicationView(
                application.getId(), application.getApplicant().getId(), application.getApplicant().getUsername(),
                application.getName(), application.getMajor(), application.getClassName(), application.getGrade(),
                application.getContact(), application.getInterestDirections(), application.getExistingSkills(),
                application.getExperience(), application.getIntendedTags(), application.getStage(), interview,
                application.getLinkedQuizId(), application.getConvertedMemberId(), application.getCreatedAt(),
                application.getUpdatedAt(), history
        );
    }

    private List<String> cleanList(List<String> values) {
        return values.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private RecruitmentStatusHistoryEntity.AccountEntitySnapshot snapshot(AccountEntity account) {
        return new RecruitmentStatusHistoryEntity.AccountEntitySnapshot(account.getId(), account.getUsername());
    }

    private static Map<RecruitmentStage, EnumSet<RecruitmentStage>> transitions() {
        Map<RecruitmentStage, EnumSet<RecruitmentStage>> values = new EnumMap<>(RecruitmentStage.class);
        values.put(RecruitmentStage.SIGNUP, EnumSet.of(RecruitmentStage.SCREENING, RecruitmentStage.REJECTED));
        values.put(RecruitmentStage.SCREENING, EnumSet.of(RecruitmentStage.INTERVIEW, RecruitmentStage.REJECTED));
        values.put(RecruitmentStage.INTERVIEW, EnumSet.of(RecruitmentStage.SKILL_TEST, RecruitmentStage.REJECTED));
        values.put(RecruitmentStage.SKILL_TEST, EnumSet.of(RecruitmentStage.PROBATION, RecruitmentStage.REJECTED));
        values.put(RecruitmentStage.PROBATION, EnumSet.of(RecruitmentStage.FORMAL_MEMBER, RecruitmentStage.REJECTED));
        return values;
    }
}

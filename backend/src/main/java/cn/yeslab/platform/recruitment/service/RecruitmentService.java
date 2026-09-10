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
import cn.yeslab.platform.recruitment.model.RecruitmentPortfolioImageEntity;
import cn.yeslab.platform.recruitment.model.RecruitmentStage;
import cn.yeslab.platform.recruitment.model.RecruitmentStatusHistoryEntity;
import cn.yeslab.platform.recruitment.repository.RecruitmentApplicationRepository;
import cn.yeslab.platform.recruitment.repository.RecruitmentPortfolioImageRepository;
import cn.yeslab.platform.recruitment.repository.RecruitmentStatusHistoryRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
public class RecruitmentService {

    private static final Map<RecruitmentStage, EnumSet<RecruitmentStage>> ALLOWED_TRANSITIONS = transitions();
    private static final List<RecruitmentModels.TechnicalQuestionView> TECHNICAL_QUESTIONS = List.of(
            question("uav", "你对无人机系统有哪些了解？"),
            question("ros", "你对 ROS / ROS2 的了解和使用程度如何？"),
            question("embedded", "你是否接触过嵌入式开发、单片机或传感器？"),
            question("linux-git", "你是否了解 Linux、Git 和基本命令行操作？"),
            question("vision-ai", "你对计算机视觉、人工智能或深度学习有哪些认识？"),
            question("troubleshooting", "遇到设备或程序故障时，你通常如何排查？"),
            question("proud-project", "你做过最有成就感的实践项目是什么？"),
            question("first-three-months", "你希望加入实验室后，前三个月重点学习或完成什么？"),
            question("air-ground", "你如何理解无人机与机器狗之间的协同？"),
            question("boards", "你是否使用过 Arduino、STM32、树莓派或其他开发板？"),
            question("autopilot-sim", "你是否接触过 PX4、ArduPilot、Gazebo 或其他仿真平台？"),
            question("english-docs", "你能否阅读英文技术文档？"),
            question("learning", "你通常通过什么方式学习一项新技术？"),
            question("failure", "请描述一次解决技术问题或失败后重新尝试的经历。"),
            question("strength", "你更擅长算法、硬件、结构设计、调试还是内容展示？"),
            question("collaboration", "你更喜欢独立完成任务，还是参与团队协作？为什么？"),
            question("unknown-task", "面对一个完全陌生的任务，你会如何开始？"),
            question("weekly-time", "你每周可以为实验室投入多少时间？"),
            question("project-type", "你希望参与科研、竞赛、工程项目还是开源项目？"),
            question("team-value", "除了技术能力，你还能为团队带来什么？")
    );

    private final RecruitmentApplicationRepository applications;
    private final RecruitmentStatusHistoryRepository histories;
    private final RecruitmentPortfolioImageRepository portfolioImages;
    private final AccountRepository accounts;
    private final MemberProfileRepository profiles;
    private final AuthService authService;
    private final RecruitmentPortfolioStorageService portfolioStorage;
    private final ObjectMapper objectMapper;

    public RecruitmentService(
            RecruitmentApplicationRepository applications,
            RecruitmentStatusHistoryRepository histories,
            RecruitmentPortfolioImageRepository portfolioImages,
            AccountRepository accounts,
            MemberProfileRepository profiles,
            AuthService authService,
            RecruitmentPortfolioStorageService portfolioStorage,
            ObjectMapper objectMapper
    ) {
        this.applications = applications;
        this.histories = histories;
        this.portfolioImages = portfolioImages;
        this.accounts = accounts;
        this.profiles = profiles;
        this.authService = authService;
        this.portfolioStorage = portfolioStorage;
        this.objectMapper = objectMapper;
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_VIEW')")
    @Transactional(readOnly = true)
    public List<RecruitmentModels.TechnicalQuestionView> getOwnQuestions(Authentication authentication) {
        AccountEntity account = authService.requireAccount(authentication);
        return applications.findByApplicantId(account.getId())
                .map(application -> questionsFor(application, account.getId()))
                .orElseGet(() -> selectQuestions(account.getId()));
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
        List<RecruitmentModels.TechnicalQuestionView> selectedQuestions = application == null
                ? selectQuestions(account.getId()) : questionsFor(application, account.getId());
        List<String> expectedIds = selectedQuestions.stream().map(RecruitmentModels.TechnicalQuestionView::id).toList();
        validateTechnicalAnswers(request, expectedIds);
        List<RecruitmentModels.MediaLinkRequest> mediaLinks = validateMediaLinks(request.mediaLinks());

        if (application == null) {
            application = applications.save(new RecruitmentApplicationEntity(
                    account, request.name().trim(), request.major().trim(), request.className().trim(), normalize(request.grade()),
                    request.email().trim().toLowerCase(java.util.Locale.ROOT), cleanList(request.interestDirections()), cleanList(request.existingSkills()),
                    normalize(request.experience()), cleanList(request.intendedTags())
            ));
            histories.save(new RecruitmentStatusHistoryEntity(
                    application.getId(), null, RecruitmentStage.SIGNUP, snapshot(account), "提交报名表"
            ));
        } else {
            application.updateApplication(
                    request.name().trim(), request.major().trim(), request.className().trim(), normalize(request.grade()),
                    request.email().trim().toLowerCase(java.util.Locale.ROOT), cleanList(request.interestDirections()), cleanList(request.existingSkills()),
                    normalize(request.experience()), cleanList(request.intendedTags())
            );
            application = applications.save(application);
        }
        application.updateContactDetails(request.email().trim().toLowerCase(java.util.Locale.ROOT),
                normalize(request.phone()), normalize(request.wechat()), normalize(request.selfIntroduction()));
        application.updatePersonalShowcase(normalize(request.portfolioIntroduction()), writeJson(mediaLinks),
                writeJson(expectedIds), writeJson(request.technicalAnswers().stream()
                        .map(answer -> new RecruitmentModels.TechnicalAnswerRequest(answer.questionId(), answer.answer().trim())).toList()));
        return toView(applications.save(application));
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_EDIT')")
    @Transactional
    public RecruitmentModels.ApplicationView uploadOwnPortfolio(Authentication authentication, List<MultipartFile> files) {
        AccountEntity account = authService.requireAccount(authentication);
        RecruitmentApplicationEntity application = applications.findByApplicantId(account.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "请先保存报名表，再上传作品图片"));
        if (application.getStage() != RecruitmentStage.SIGNUP) throw new ApiException(HttpStatus.CONFLICT, "报名表进入初筛后不能修改作品图片");
        List<MultipartFile> cleanFiles = files == null ? List.of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        long existing = portfolioImages.countByApplicationId(application.getId());
        if (cleanFiles.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择作品图片");
        if (existing + cleanFiles.size() > 6) throw new ApiException(HttpStatus.BAD_REQUEST, "作品图片最多上传 6 张");
        List<String> storedNames = new ArrayList<>();
        try {
            int order = (int) existing;
            for (MultipartFile file : cleanFiles) {
                var stored = portfolioStorage.store(file); storedNames.add(stored.storedName());
                portfolioImages.save(new RecruitmentPortfolioImageEntity(application.getId(), stored.storedName(),
                        stored.originalName(), stored.contentType(), stored.sizeBytes(), order++));
            }
        } catch (RuntimeException error) { storedNames.forEach(portfolioStorage::delete); throw error; }
        return toView(application);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_EDIT')")
    @Transactional
    public RecruitmentModels.ApplicationView deleteOwnPortfolioImage(Authentication authentication, UUID imageId) {
        AccountEntity account = authService.requireAccount(authentication);
        RecruitmentApplicationEntity application = applications.findByApplicantId(account.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "报名记录不存在"));
        if (application.getStage() != RecruitmentStage.SIGNUP) throw new ApiException(HttpStatus.CONFLICT, "报名表进入初筛后不能修改作品图片");
        RecruitmentPortfolioImageEntity image = portfolioImages.findByIdAndApplicationId(imageId, application.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "作品图片不存在"));
        portfolioImages.delete(image); portfolioStorage.delete(image.getStoredName());
        return toView(application);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public FileDownload portfolioImage(Authentication authentication, UUID applicationId, UUID imageId) {
        AccountEntity account = authService.requireAccount(authentication);
        RecruitmentApplicationEntity application = requireApplication(applicationId);
        if (!account.getRole().isSystemAdmin() && !application.getApplicant().getId().equals(account.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "没有权限查看该作品图片");
        }
        RecruitmentPortfolioImageEntity image = portfolioImages.findByIdAndApplicationId(imageId, applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "作品图片不存在"));
        return new FileDownload(portfolioStorage.resource(image.getStoredName()), image.getOriginalName(), image.getContentType());
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional(readOnly = true)
    public List<RecruitmentModels.ApplicationView> listApplications() {
        return applications.findAll().stream()
                .filter(application -> !Boolean.TRUE.equals(application.getInterviewPassed()))
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
    public void resetApplicantPassword(UUID applicationId) {
        RecruitmentApplicationEntity application = requireApplication(applicationId);
        AccountEntity applicant = application.getApplicant();
        if (application.getStage() == RecruitmentStage.FORMAL_MEMBER
                || applicant.getRole() != Role.VISITOR
                || profiles.findByAccountId(applicant.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "该报名账号已转为正式成员，请在成员管理中重置密码");
        }
        authService.resetPasswordToDefault(applicant);
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

    @Transactional
    public RecruitmentModels.ApplicationView recordScheduledInterview(
            AccountEntity operator,
            UUID applicationId,
            Integer score,
            String evaluation,
            List<String> suggestedTags,
            boolean passed
    ) {
        RecruitmentApplicationEntity application = requireApplication(applicationId);
        if (application.getStage() != RecruitmentStage.INTERVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "只有面试阶段可以提交面试结论");
        }
        String cleanEvaluation = normalize(evaluation);
        if (passed && cleanEvaluation == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "通过面试时必须填写简评");
        }
        application.recordInterview(operator, score, cleanEvaluation, cleanList(suggestedTags), passed);
        changeStage(application, passed ? RecruitmentStage.SKILL_TEST : RecruitmentStage.REJECTED,
                operator, passed ? "面试通过" : "面试未通过");
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
        List<RecruitmentModels.PortfolioImageView> images = portfolioImages
                .findByApplicationIdOrderByDisplayOrderAsc(application.getId()).stream()
                .map(image -> new RecruitmentModels.PortfolioImageView(image.getId(), image.getOriginalName(),
                        image.getContentType(), image.getSizeBytes(), image.getDisplayOrder(),
                        "/api/v1/recruitment/applications/" + application.getId() + "/portfolio-images/" + image.getId()))
                .toList();
        return new RecruitmentModels.ApplicationView(
                application.getId(), application.getApplicant().getId(), application.getApplicant().getUsername(),
                application.getName(), application.getMajor(), application.getClassName(), application.getGrade(),
                application.getContact(), application.getEmail(), application.getPhone(), application.getWechat(),
                application.getSelfIntroduction(), application.getInterestDirections(), application.getExistingSkills(),
                application.getExperience(), application.getIntendedTags(), application.getPortfolioIntroduction(),
                readJson(application.getMediaLinksJson(), new TypeReference<List<RecruitmentModels.MediaLinkRequest>>() {}, List.of()),
                questionsFor(application, application.getApplicant().getId()),
                readJson(application.getTechnicalAnswersJson(), new TypeReference<List<RecruitmentModels.TechnicalAnswerRequest>>() {}, List.of()),
                images, application.getStage(), interview,
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

    private void validateTechnicalAnswers(RecruitmentModels.ApplicationRequest request, List<String> expectedIds) {
        if (!new HashSet<>(request.technicalQuestionIds()).equals(new HashSet<>(expectedIds))
                || request.technicalQuestionIds().size() != 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "技术认知题目与当前分配不一致，请刷新后重试");
        }
        List<RecruitmentModels.TechnicalAnswerRequest> answers = request.technicalAnswers();
        long unique = answers.stream().map(RecruitmentModels.TechnicalAnswerRequest::questionId).distinct().count();
        if (unique < 3 || unique != answers.size() || answers.stream().anyMatch(answer -> !expectedIds.contains(answer.questionId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请从分配的 5 道题中任选至少 3 道作答");
        }
    }

    private List<RecruitmentModels.MediaLinkRequest> validateMediaLinks(List<RecruitmentModels.MediaLinkRequest> values) {
        List<RecruitmentModels.MediaLinkRequest> result = new ArrayList<>();
        for (RecruitmentModels.MediaLinkRequest value : values) {
            String url = value.url().trim();
            try {
                URI uri = URI.create(url);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException error) { throw new ApiException(HttpStatus.BAD_REQUEST, "个人主页链接必须是完整的 HTTP 或 HTTPS 地址"); }
            result.add(new RecruitmentModels.MediaLinkRequest(value.platform().trim(), normalize(value.account()), url));
        }
        return result.stream().distinct().toList();
    }

    private List<RecruitmentModels.TechnicalQuestionView> questionsFor(RecruitmentApplicationEntity application, UUID accountId) {
        List<String> ids = readJson(application.getTechnicalQuestionIdsJson(), new TypeReference<List<String>>() {}, List.of());
        if (ids.size() != 5) return selectQuestions(accountId);
        Map<String, RecruitmentModels.TechnicalQuestionView> byId = TECHNICAL_QUESTIONS.stream()
                .collect(java.util.stream.Collectors.toMap(RecruitmentModels.TechnicalQuestionView::id, item -> item));
        List<RecruitmentModels.TechnicalQuestionView> result = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        return result.size() == 5 ? result : selectQuestions(accountId);
    }

    private List<RecruitmentModels.TechnicalQuestionView> selectQuestions(UUID accountId) {
        List<RecruitmentModels.TechnicalQuestionView> shuffled = new ArrayList<>(TECHNICAL_QUESTIONS);
        Collections.shuffle(shuffled, new Random(accountId.getMostSignificantBits() ^ accountId.getLeastSignificantBits()));
        return List.copyOf(shuffled.subList(0, 5));
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception error) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "报名资料序列化失败"); }
    }

    private <T> T readJson(String value, TypeReference<T> type, T fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return objectMapper.readValue(value, type); }
        catch (Exception ignored) { return fallback; }
    }

    private static RecruitmentModels.TechnicalQuestionView question(String id, String prompt) {
        return new RecruitmentModels.TechnicalQuestionView(id, prompt);
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

    public record FileDownload(Resource resource, String originalName, String contentType) { }
}

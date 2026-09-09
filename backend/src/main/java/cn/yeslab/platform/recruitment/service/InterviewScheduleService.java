package cn.yeslab.platform.recruitment.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.repository.AccountRepository;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.notification.service.NotificationService;
import cn.yeslab.platform.recruitment.api.InterviewScheduleModels;
import cn.yeslab.platform.recruitment.model.InterviewBookingEntity;
import cn.yeslab.platform.recruitment.model.InterviewBookingStatus;
import cn.yeslab.platform.recruitment.model.InterviewSessionEntity;
import cn.yeslab.platform.recruitment.model.InterviewSessionStatus;
import cn.yeslab.platform.recruitment.model.RecruitmentApplicationEntity;
import cn.yeslab.platform.recruitment.model.RecruitmentStage;
import cn.yeslab.platform.recruitment.repository.InterviewBookingRepository;
import cn.yeslab.platform.recruitment.repository.InterviewSessionRepository;
import cn.yeslab.platform.recruitment.repository.RecruitmentApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class InterviewScheduleService {

    private static final List<InterviewBookingStatus> OPEN_BOOKING_STATUSES = List.of(
            InterviewBookingStatus.WAITING, InterviewBookingStatus.CALLED, InterviewBookingStatus.IN_PROGRESS);

    private final InterviewSessionRepository sessions;
    private final InterviewBookingRepository bookings;
    private final RecruitmentApplicationRepository applications;
    private final AccountRepository accounts;
    private final MemberProfileRepository profiles;
    private final AuthService authService;
    private final NotificationService notificationService;
    private final RecruitmentService recruitmentService;

    public InterviewScheduleService(InterviewSessionRepository sessions, InterviewBookingRepository bookings,
                                    RecruitmentApplicationRepository applications, AccountRepository accounts,
                                    MemberProfileRepository profiles, AuthService authService,
                                    NotificationService notificationService, RecruitmentService recruitmentService) {
        this.sessions = sessions;
        this.bookings = bookings;
        this.applications = applications;
        this.accounts = accounts;
        this.profiles = profiles;
        this.authService = authService;
        this.notificationService = notificationService;
        this.recruitmentService = recruitmentService;
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional(readOnly = true)
    public List<InterviewScheduleModels.AdminSessionView> listAdmin(Authentication authentication) {
        AccountEntity operator = authService.requireAccount(authentication);
        return sessions.findAll().stream()
                .sorted(Comparator.comparing(InterviewSessionEntity::getStartAt).reversed())
                .map(session -> toAdminView(session, operator)).toList();
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional
    public InterviewScheduleModels.AdminSessionView create(Authentication authentication,
                                                            InterviewScheduleModels.SessionRequest request) {
        AccountEntity publisher = authService.requireAccount(authentication);
        validateTiming(request.startAt(), request.endAt());
        Set<AccountEntity> interviewers = resolveInterviewers(request.interviewerUsernames());
        if (interviewers.stream().noneMatch(account -> account.getId().equals(publisher.getId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "发布者必须参加该场面试");
        }
        InterviewSessionEntity session = sessions.save(new InterviewSessionEntity(publisher, interviewers,
                request.startAt(), request.endAt(), request.location().trim(), request.capacity()));
        return toAdminView(session, publisher);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional
    public InterviewScheduleModels.AdminSessionView update(Authentication authentication, UUID sessionId,
                                                            InterviewScheduleModels.SessionRequest request) {
        AccountEntity operator = authService.requireAccount(authentication);
        InterviewSessionEntity session = requireSession(sessionId);
        requireInterviewer(session, operator);
        if (session.getStatus() != InterviewSessionStatus.SCHEDULED) {
            throw new ApiException(HttpStatus.CONFLICT, "只有尚未开始的面试场次可以修改");
        }
        validateTiming(request.startAt(), request.endAt());
        Set<AccountEntity> interviewers = resolveInterviewers(request.interviewerUsernames());
        if (interviewers.stream().noneMatch(account -> account.getId().equals(session.getPublisher().getId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "发布者必须保留为面试官");
        }
        long bookedCount = bookings.countBySessionId(sessionId);
        if (request.capacity() < bookedCount) {
            throw new ApiException(HttpStatus.CONFLICT, "面试人数不能少于当前已预约人数");
        }
        boolean scheduleChanged = !session.getStartAt().equals(request.startAt())
                || !session.getEndAt().equals(request.endAt())
                || !session.getLocation().equals(request.location().trim());
        session.update(interviewers, request.startAt(), request.endAt(), request.location().trim(), request.capacity());
        sessions.save(session);
        if (scheduleChanged) {
            for (InterviewBookingEntity booking : bookings.findBySessionIdOrderByQueueNumberAsc(sessionId)) {
                if (booking.getStatus() != InterviewBookingStatus.COMPLETED) {
                    notificationService.send(booking.getApplication().getApplicant(), "INTERVIEW_CHANGED", "面试安排已变更",
                            "你的面试时间或地点已调整，请打开预约页面查看最新安排。", "/application");
                }
            }
        }
        return toAdminView(session, operator);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional
    public void cancel(Authentication authentication, UUID sessionId) {
        AccountEntity operator = authService.requireAccount(authentication);
        InterviewSessionEntity session = requireSession(sessionId);
        requireInterviewer(session, operator);
        if (session.getStatus() == InterviewSessionStatus.COMPLETED || session.getStatus() == InterviewSessionStatus.CANCELLED
                || session.getStatus() == InterviewSessionStatus.ENDED_EARLY) {
            throw new ApiException(HttpStatus.CONFLICT, "该场面试已经结束");
        }
        releaseOpenBookings(session, "原面试场次已取消，请重新选择新的面试时间。");
        session.cancel();
        sessions.save(session);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_VIEW')")
    @Transactional
    public InterviewScheduleModels.ApplicantScheduleView applicantSchedule(Authentication authentication) {
        AccountEntity account = authService.requireAccount(authentication);
        RecruitmentApplicationEntity application = applications.findByApplicantId(account.getId()).orElse(null);
        if (application == null || application.getStage() != RecruitmentStage.INTERVIEW) {
            return new InterviewScheduleModels.ApplicantScheduleView(false, "简历通过后可以预约面试。", null, List.of());
        }
        InterviewBookingEntity ownBooking = bookings.findByApplicationId(application.getId()).orElse(null);
        if (ownBooking != null) {
            return new InterviewScheduleModels.ApplicantScheduleView(true, "你已经预约面试；取消后才能重新选择。",
                    toApplicantBooking(ownBooking), List.of());
        }

        Instant bookingDeadline = Instant.now().plus(Duration.ofHours(1));
        List<InterviewScheduleModels.ApplicantSessionView> available = sessions
                .findByStatusInOrderByStartAtAsc(List.of(InterviewSessionStatus.SCHEDULED)).stream()
                .filter(session -> session.getStartAt().isAfter(bookingDeadline))
                .filter(session -> bookings.countBySessionId(session.getId()) < session.getCapacity())
                .map(this::toApplicantSession).toList();
        if (available.isEmpty()) {
            notificationService.notifyAdminsNoInterviewSlots(application.getId(), application.getName());
        }
        return new InterviewScheduleModels.ApplicantScheduleView(true,
                available.isEmpty() ? "目前没有可预约的面试场次，请稍后再来查看。" : "请选择一个面试场次。",
                null, available);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_VIEW')")
    @Transactional
    public InterviewScheduleModels.ApplicantScheduleView book(Authentication authentication, UUID sessionId) {
        AccountEntity account = authService.requireAccount(authentication);
        RecruitmentApplicationEntity application = applications.findByApplicantId(account.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "请先提交报名表"));
        if (application.getStage() != RecruitmentStage.INTERVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "只有简历通过且处于面试阶段时可以预约");
        }
        if (bookings.findByApplicationId(application.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "你已经预约面试，请先取消原预约");
        }
        InterviewSessionEntity session = sessions.findLockedById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "面试场次不存在"));
        if (session.getStatus() != InterviewSessionStatus.SCHEDULED
                || !session.getStartAt().isAfter(Instant.now().plus(Duration.ofHours(1)))) {
            throw new ApiException(HttpStatus.CONFLICT, "该场面试已经停止预约");
        }
        if (bookings.countBySessionId(sessionId) >= session.getCapacity()) {
            throw new ApiException(HttpStatus.CONFLICT, "该场面试人数已满");
        }
        bookings.save(new InterviewBookingEntity(session, application, session.takeNextQueueNumber()));
        sessions.save(session);
        return applicantSchedule(authentication);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_VIEW')")
    @Transactional
    public InterviewScheduleModels.ApplicantScheduleView cancelOwnBooking(Authentication authentication) {
        AccountEntity account = authService.requireAccount(authentication);
        RecruitmentApplicationEntity application = applications.findByApplicantId(account.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "报名记录不存在"));
        InterviewBookingEntity booking = bookings.findByApplicationId(application.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "你目前没有面试预约"));
        if (!Instant.now().isBefore(booking.getSession().getStartAt())
                || booking.getStatus() == InterviewBookingStatus.IN_PROGRESS
                || booking.getStatus() == InterviewBookingStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "面试开始后不能自行取消预约");
        }
        bookings.delete(booking);
        bookings.flush();
        return applicantSchedule(authentication);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional
    public InterviewScheduleModels.AdminSessionView callNext(Authentication authentication, UUID sessionId) {
        AccountEntity operator = authService.requireAccount(authentication);
        InterviewSessionEntity session = requireSession(sessionId);
        requireInterviewer(session, operator);
        requireOperable(session);
        if (Instant.now().isBefore(session.getStartAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "尚未到面试开始时间");
        }
        if (bookings.findFirstBySessionIdAndStatusInOrderByQueueNumberAsc(sessionId,
                List.of(InterviewBookingStatus.CALLED, InterviewBookingStatus.IN_PROGRESS)).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "当前叫号尚未处理完成");
        }
        InterviewBookingEntity next = bookings.findFirstBySessionIdAndStatusInOrderByQueueNumberAsc(sessionId,
                        List.of(InterviewBookingStatus.WAITING))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "队列中没有等待面试的人员"));
        next.call();
        session.activate();
        bookings.save(next);
        sessions.save(session);
        return toAdminView(session, operator);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional
    public InterviewScheduleModels.AdminSessionView startInterview(Authentication authentication, UUID sessionId, UUID bookingId) {
        AccountEntity operator = authService.requireAccount(authentication);
        InterviewSessionEntity session = requireSession(sessionId);
        requireInterviewer(session, operator);
        InterviewBookingEntity booking = requireBooking(sessionId, bookingId);
        if (booking.getStatus() != InterviewBookingStatus.CALLED) {
            throw new ApiException(HttpStatus.CONFLICT, "只有当前叫号人员可以开始面试");
        }
        booking.start();
        bookings.save(booking);
        return toAdminView(session, operator);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional
    public InterviewScheduleModels.AdminSessionView noShow(Authentication authentication, UUID sessionId, UUID bookingId) {
        AccountEntity operator = authService.requireAccount(authentication);
        InterviewSessionEntity session = requireSession(sessionId);
        requireInterviewer(session, operator);
        InterviewBookingEntity booking = requireBooking(sessionId, bookingId);
        if (booking.getStatus() != InterviewBookingStatus.CALLED) {
            throw new ApiException(HttpStatus.CONFLICT, "只有当前叫号人员可以移至队尾");
        }
        booking.moveToQueueTail(session.takeNextQueueNumber());
        bookings.save(booking);
        sessions.save(session);
        return toAdminView(session, operator);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional
    public InterviewScheduleModels.AdminSessionView completeInterview(Authentication authentication, UUID sessionId,
                                                                       UUID bookingId,
                                                                       InterviewScheduleModels.InterviewResultRequest request) {
        AccountEntity operator = authService.requireAccount(authentication);
        InterviewSessionEntity session = requireSession(sessionId);
        requireInterviewer(session, operator);
        InterviewBookingEntity booking = requireBooking(sessionId, bookingId);
        if (booking.getStatus() != InterviewBookingStatus.IN_PROGRESS && booking.getStatus() != InterviewBookingStatus.CALLED) {
            throw new ApiException(HttpStatus.CONFLICT, "该报名者当前不在面试中");
        }
        String evaluation = normalize(request.evaluation());
        if (Boolean.TRUE.equals(request.passed()) && evaluation == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "通过面试时必须填写简评");
        }
        recruitmentService.recordScheduledInterview(operator, booking.getApplication().getId(), request.score(),
                evaluation, request.suggestedTags(), request.passed());
        booking.complete();
        bookings.save(booking);
        AccountEntity applicant = booking.getApplication().getApplicant();
        if (request.passed()) {
            notificationService.send(applicant, "INTERVIEW_PASSED", "你已通过面试",
                    evaluation, "/application");
        } else {
            notificationService.send(applicant, "INTERVIEW_RESULT", "面试结果已更新",
                    evaluation == null ? "本轮招新流程已结束。" : evaluation, "/application");
        }
        if (bookings.countBySessionIdAndStatusIn(sessionId, OPEN_BOOKING_STATUSES) == 0) {
            session.complete();
            sessions.save(session);
        }
        return toAdminView(session, operator);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional
    public InterviewScheduleModels.AdminSessionView endEarly(Authentication authentication, UUID sessionId) {
        AccountEntity operator = authService.requireAccount(authentication);
        InterviewSessionEntity session = requireSession(sessionId);
        requireInterviewer(session, operator);
        requireOperable(session);
        releaseOpenBookings(session, "本场面试已提前结束，你的预约已释放，请重新选择面试场次。");
        session.endEarly();
        sessions.save(session);
        return toAdminView(session, operator);
    }

    private void releaseOpenBookings(InterviewSessionEntity session, String message) {
        List<InterviewBookingEntity> released = bookings.findBySessionIdOrderByQueueNumberAsc(session.getId()).stream()
                .filter(booking -> booking.getStatus() != InterviewBookingStatus.COMPLETED).toList();
        for (InterviewBookingEntity booking : released) {
            notificationService.send(booking.getApplication().getApplicant(), "INTERVIEW_RELEASED", "需要重新预约面试",
                    message, "/application");
        }
        bookings.deleteAll(released);
        bookings.flush();
    }

    private InterviewScheduleModels.AdminSessionView toAdminView(InterviewSessionEntity session, AccountEntity operator) {
        List<InterviewScheduleModels.QueueEntryView> queue = bookings.findBySessionIdOrderByQueueNumberAsc(session.getId()).stream()
                .map(booking -> new InterviewScheduleModels.QueueEntryView(booking.getId(), booking.getApplication().getId(),
                        booking.getApplication().getName(), booking.getQueueNumber(), booking.getStatus(), booking.getBookedAt()))
                .toList();
        List<InterviewScheduleModels.InterviewerView> interviewerViews = session.getInterviewers().stream()
                .sorted(Comparator.comparing(AccountEntity::getUsername))
                .map(this::toInterviewer).toList();
        return new InterviewScheduleModels.AdminSessionView(session.getId(), session.getStartAt(), session.getEndAt(),
                session.getLocation(), session.getCapacity(), queue.size(), session.getStatus(),
                session.getPublisher().getUsername(), interviewerViews, queue,
                session.getInterviewers().stream().anyMatch(item -> item.getId().equals(operator.getId())));
    }

    private InterviewScheduleModels.ApplicantSessionView toApplicantSession(InterviewSessionEntity session) {
        int booked = (int) bookings.countBySessionId(session.getId());
        return new InterviewScheduleModels.ApplicantSessionView(session.getId(), session.getStartAt(), session.getEndAt(),
                Math.max(0, session.getCapacity() - booked), session.getStatus());
    }

    private InterviewScheduleModels.ApplicantBookingView toApplicantBooking(InterviewBookingEntity booking) {
        InterviewBookingEntity called = bookings.findFirstBySessionIdAndStatusInOrderByQueueNumberAsc(booking.getSession().getId(),
                List.of(InterviewBookingStatus.CALLED, InterviewBookingStatus.IN_PROGRESS)).orElse(null);
        return new InterviewScheduleModels.ApplicantBookingView(booking.getId(), booking.getSession().getId(),
                booking.getSession().getStartAt(), booking.getSession().getEndAt(), booking.getSession().getLocation(),
                booking.getQueueNumber(), called == null ? null : called.getQueueNumber(), booking.getStatus(),
                Instant.now().isBefore(booking.getSession().getStartAt())
                        && booking.getStatus() != InterviewBookingStatus.IN_PROGRESS
                        && booking.getStatus() != InterviewBookingStatus.COMPLETED);
    }

    private InterviewScheduleModels.InterviewerView toInterviewer(AccountEntity account) {
        String name = profiles.findByAccountId(account.getId()).map(MemberProfileEntity::getName).orElse(account.getUsername());
        return new InterviewScheduleModels.InterviewerView(account.getId(), account.getUsername(), name, account.getRole().name());
    }

    private Set<AccountEntity> resolveInterviewers(List<String> usernames) {
        Set<AccountEntity> result = new LinkedHashSet<>();
        for (String username : usernames) {
            AccountEntity account = accounts.findByUsernameIgnoreCase(username.trim())
                    .filter(AccountEntity::isEnabled)
                    .filter(item -> item.getRole().isSystemAdmin())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "面试官必须是指导老师或核心成员"));
            result.add(account);
        }
        if (result.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "至少选择一名面试官");
        return result;
    }

    private void validateTiming(Instant startAt, Instant endAt) {
        Instant now = Instant.now();
        if (!startAt.isAfter(now)) throw new ApiException(HttpStatus.BAD_REQUEST, "面试开始时间必须晚于当前时间");
        if (startAt.isAfter(now.plus(Duration.ofDays(14)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "面试日期必须在未来十四天内");
        }
        if (!endAt.isAfter(startAt)) throw new ApiException(HttpStatus.BAD_REQUEST, "面试结束时间必须晚于开始时间");
    }

    private InterviewSessionEntity requireSession(UUID sessionId) {
        return sessions.findById(sessionId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "面试场次不存在"));
    }

    private InterviewBookingEntity requireBooking(UUID sessionId, UUID bookingId) {
        return bookings.findByIdAndSessionId(bookingId, sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "面试预约不存在"));
    }

    private void requireInterviewer(InterviewSessionEntity session, AccountEntity operator) {
        if (session.getInterviewers().stream().noneMatch(item -> item.getId().equals(operator.getId()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有本场面试官可以执行该操作");
        }
    }

    private void requireOperable(InterviewSessionEntity session) {
        if (session.getStatus() == InterviewSessionStatus.CANCELLED
                || session.getStatus() == InterviewSessionStatus.COMPLETED
                || session.getStatus() == InterviewSessionStatus.ENDED_EARLY) {
            throw new ApiException(HttpStatus.CONFLICT, "该场面试已经结束");
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }
}

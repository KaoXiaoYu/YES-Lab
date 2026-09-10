package cn.yeslab.platform.recruitment.service;

import cn.yeslab.platform.recruitment.model.InterviewSessionEntity;
import cn.yeslab.platform.recruitment.model.InterviewSessionStatus;
import cn.yeslab.platform.recruitment.repository.InterviewBookingRepository;
import cn.yeslab.platform.recruitment.repository.InterviewSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class InterviewRetentionService {
    private static final List<InterviewSessionStatus> EXPIRING_STATUSES = List.of(
            InterviewSessionStatus.CANCELLED, InterviewSessionStatus.ENDED_EARLY);

    private final InterviewSessionRepository sessions;
    private final InterviewBookingRepository bookings;

    public InterviewRetentionService(InterviewSessionRepository sessions, InterviewBookingRepository bookings) {
        this.sessions = sessions;
        this.bookings = bookings;
    }

    @Transactional
    public int purgeExpiredBefore(Instant cutoff) {
        List<InterviewSessionEntity> expired = sessions.findByStatusInAndUpdatedAtBefore(EXPIRING_STATUSES, cutoff);
        expired.forEach(session -> bookings.deleteBySessionId(session.getId()));
        sessions.deleteAll(expired);
        return expired.size();
    }
}

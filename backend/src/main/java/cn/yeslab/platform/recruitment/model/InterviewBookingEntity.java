package cn.yeslab.platform.recruitment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interview_bookings")
public class InterviewBookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSessionEntity session;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private RecruitmentApplicationEntity application;

    @Column(nullable = false)
    private int queueNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InterviewBookingStatus status = InterviewBookingStatus.WAITING;

    @Column(nullable = false, updatable = false)
    private Instant bookedAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected InterviewBookingEntity() { }

    public InterviewBookingEntity(InterviewSessionEntity session, RecruitmentApplicationEntity application, int queueNumber) {
        this.session = session;
        this.application = application;
        this.queueNumber = queueNumber;
    }

    public UUID getId() { return id; }
    public InterviewSessionEntity getSession() { return session; }
    public RecruitmentApplicationEntity getApplication() { return application; }
    public int getQueueNumber() { return queueNumber; }
    public InterviewBookingStatus getStatus() { return status; }
    public Instant getBookedAt() { return bookedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void call() { status = InterviewBookingStatus.CALLED; updatedAt = Instant.now(); }
    public void start() { status = InterviewBookingStatus.IN_PROGRESS; updatedAt = Instant.now(); }
    public void complete() { status = InterviewBookingStatus.COMPLETED; updatedAt = Instant.now(); }
    public void moveToQueueTail(int newQueueNumber) {
        queueNumber = newQueueNumber;
        status = InterviewBookingStatus.WAITING;
        updatedAt = Instant.now();
    }
}

package cn.yeslab.platform.recruitment.model;

import cn.yeslab.platform.identity.model.AccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "interview_sessions")
public class InterviewSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_account_id", nullable = false)
    private AccountEntity publisher;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "interview_session_interviewers",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "account_id"))
    private Set<AccountEntity> interviewers = new LinkedHashSet<>();

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Column(nullable = false, length = 240)
    private String location;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int nextQueueNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InterviewSessionStatus status = InterviewSessionStatus.SCHEDULED;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected InterviewSessionEntity() { }

    public InterviewSessionEntity(AccountEntity publisher, Set<AccountEntity> interviewers, Instant startAt,
                                  Instant endAt, String location, int capacity) {
        this.publisher = publisher;
        update(interviewers, startAt, endAt, location, capacity);
    }

    public UUID getId() { return id; }
    public AccountEntity getPublisher() { return publisher; }
    public Set<AccountEntity> getInterviewers() { return Set.copyOf(interviewers); }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public String getLocation() { return location; }
    public int getCapacity() { return capacity; }
    public InterviewSessionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(Set<AccountEntity> interviewers, Instant startAt, Instant endAt, String location, int capacity) {
        this.interviewers = new LinkedHashSet<>(interviewers);
        this.startAt = startAt;
        this.endAt = endAt;
        this.location = location;
        this.capacity = capacity;
        this.updatedAt = Instant.now();
    }

    public int takeNextQueueNumber() {
        int number = nextQueueNumber++;
        updatedAt = Instant.now();
        return number;
    }

    public void activate() { this.status = InterviewSessionStatus.ACTIVE; this.updatedAt = Instant.now(); }
    public void complete() { this.status = InterviewSessionStatus.COMPLETED; this.updatedAt = Instant.now(); }
    public void cancel() { this.status = InterviewSessionStatus.CANCELLED; this.updatedAt = Instant.now(); }
    public void endEarly() { this.status = InterviewSessionStatus.ENDED_EARLY; this.updatedAt = Instant.now(); }
}

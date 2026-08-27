package cn.yeslab.platform.achievement.model;

import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.project.model.ProjectTeamEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "competitions")
public class CompetitionEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 180)
    private String track;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private CompetitionLevel level;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private CompetitionLifecycle lifecycle;

    @Column(length = 160)
    private String awardName;

    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String description;

    private LocalDate competitionDate;
    private LocalDate provincialDate;
    private LocalDate nationalDate;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "captain_profile_id", nullable = false)
    private MemberProfileEntity captainProfile;

    @Column(nullable = false, length = 80)
    private String captainName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advisor_profile_id")
    private MemberProfileEntity advisorProfile;

    @Column(length = 80)
    private String advisorName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectTeamEntity project;

    @OneToMany(mappedBy = "competition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<CompetitionParticipantEntity> participants = new ArrayList<>();

    @OneToMany(mappedBy = "competition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<CompetitionImageEntity> images = new ArrayList<>();

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private VerificationStatus verificationStatus;

    @Column(length = 500)
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_account_id")
    private AccountEntity reviewer;

    private Instant reviewedAt;

    @Column(nullable = false)
    private boolean featured;

    @Column(nullable = false)
    private int displayOrder;

    @Column(unique = true, length = 100)
    private String certificateStoredName;

    @Column(length = 255)
    private String certificateOriginalName;

    @Column(length = 80)
    private String certificateContentType;

    private Long certificateSizeBytes;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_account_id", nullable = false, updatable = false)
    private AccountEntity submittedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected CompetitionEntity() {
    }

    public CompetitionEntity(String name, CompetitionLevel level, CompetitionLifecycle lifecycle,
                             String description, MemberProfileEntity captainProfile, AccountEntity submittedBy) {
        this.name = name;
        this.level = level;
        this.lifecycle = lifecycle;
        this.description = description;
        this.captainProfile = captainProfile;
        this.captainName = captainProfile.getName();
        this.submittedBy = submittedBy;
        this.verificationStatus = lifecycle == CompetitionLifecycle.FINISHED
                ? VerificationStatus.PENDING : VerificationStatus.NOT_REQUIRED;
    }

    public void updateDetails(String name, String track, CompetitionLevel level, CompetitionLifecycle lifecycle,
                              String awardName, String description, LocalDate competitionDate,
                              LocalDate provincialDate, LocalDate nationalDate,
                              MemberProfileEntity advisorProfile, String advisorName, ProjectTeamEntity project) {
        this.name = name; this.track = track; this.level = level; this.lifecycle = lifecycle;
        this.awardName = awardName; this.description = description; this.competitionDate = competitionDate;
        this.provincialDate = provincialDate; this.nationalDate = nationalDate;
        this.advisorProfile = advisorProfile; this.advisorName = advisorName; this.project = project;
        this.updatedAt = Instant.now();
    }

    public void replaceParticipants(List<CompetitionParticipantEntity> values) {
        participants.clear();
        values.forEach(value -> { value.attachTo(this); participants.add(value); });
        this.updatedAt = Instant.now();
    }

    public void replaceImages(List<CompetitionImageEntity> values) {
        images.clear();
        values.forEach(value -> { value.attachTo(this); images.add(value); });
        this.updatedAt = Instant.now();
    }

    public void removeImage(CompetitionImageEntity image) {
        images.remove(image);
        this.updatedAt = Instant.now();
    }

    public void updateCertificate(String storedName, String originalName, String contentType, long sizeBytes, boolean resetReview) {
        this.certificateStoredName = storedName; this.certificateOriginalName = originalName;
        this.certificateContentType = contentType; this.certificateSizeBytes = sizeBytes;
        if (resetReview && lifecycle == CompetitionLifecycle.FINISHED) {
            this.verificationStatus = VerificationStatus.PENDING;
            this.featured = false;
        }
        this.updatedAt = Instant.now();
    }

    public void markPending() { this.verificationStatus = VerificationStatus.PENDING; this.featured = false; this.updatedAt = Instant.now(); }
    public void markNotRequired() { this.verificationStatus = VerificationStatus.NOT_REQUIRED; this.featured = false; this.updatedAt = Instant.now(); }
    public void review(VerificationStatus status, String note, AccountEntity reviewer) {
        this.verificationStatus = status; this.reviewNote = note; this.reviewer = reviewer; this.reviewedAt = Instant.now();
        if (status != VerificationStatus.APPROVED) this.featured = false;
        this.updatedAt = Instant.now();
    }
    public void updateDisplay(boolean featured, int displayOrder) { this.featured = featured; this.displayOrder = displayOrder; this.updatedAt = Instant.now(); }

    public UUID getId() { return id; } public String getName() { return name; } public String getTrack() { return track; }
    public CompetitionLevel getLevel() { return level; } public CompetitionLifecycle getLifecycle() { return lifecycle; }
    public String getAwardName() { return awardName; } public String getDescription() { return description; }
    public LocalDate getCompetitionDate() { return competitionDate; } public LocalDate getProvincialDate() { return provincialDate; }
    public LocalDate getNationalDate() { return nationalDate; } public MemberProfileEntity getCaptainProfile() { return captainProfile; }
    public String getCaptainName() { return captainName; } public MemberProfileEntity getAdvisorProfile() { return advisorProfile; }
    public String getAdvisorName() { return advisorName; } public ProjectTeamEntity getProject() { return project; }
    public List<CompetitionParticipantEntity> getParticipants() { return List.copyOf(participants); }
    public List<CompetitionImageEntity> getImages() { return List.copyOf(images); }
    public VerificationStatus getVerificationStatus() { return verificationStatus; } public String getReviewNote() { return reviewNote; }
    public AccountEntity getReviewer() { return reviewer; } public Instant getReviewedAt() { return reviewedAt; }
    public boolean isFeatured() { return featured; } public int getDisplayOrder() { return displayOrder; }
    public String getCertificateStoredName() { return certificateStoredName; } public String getCertificateOriginalName() { return certificateOriginalName; }
    public String getCertificateContentType() { return certificateContentType; } public Long getCertificateSizeBytes() { return certificateSizeBytes; }
    public AccountEntity getSubmittedBy() { return submittedBy; } public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}

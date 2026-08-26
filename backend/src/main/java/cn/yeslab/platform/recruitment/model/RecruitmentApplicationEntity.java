package cn.yeslab.platform.recruitment.model;

import cn.yeslab.platform.identity.model.AccountEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recruitment_applications")
public class RecruitmentApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_account_id", nullable = false, unique = true)
    private AccountEntity applicant;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 100)
    private String major;

    @Column(name = "class_name", nullable = false, length = 100)
    private String className;

    @Column(length = 30)
    private String grade;

    @Column(nullable = false, length = 200)
    private String contact;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recruitment_interest_directions", joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "direction", nullable = false, length = 80)
    private List<String> interestDirections = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recruitment_existing_skills", joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "skill", length = 100)
    private List<String> existingSkills = new ArrayList<>();

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String experience;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recruitment_intended_tags", joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "tag", nullable = false, length = 80)
    private List<String> intendedTags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RecruitmentStage stage = RecruitmentStage.SIGNUP;

    private UUID interviewerAccountId;

    @Column(length = 80)
    private String interviewerName;

    private Integer interviewScore;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String interviewEvaluation;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recruitment_suggested_tags", joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "tag", length = 80)
    private List<String> suggestedTags = new ArrayList<>();

    private Boolean interviewPassed;

    @Column(length = 100)
    private String linkedQuizId;

    private UUID convertedMemberId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected RecruitmentApplicationEntity() {
    }

    public RecruitmentApplicationEntity(
            AccountEntity applicant,
            String name,
            String major,
            String className,
            String grade,
            String contact,
            List<String> interestDirections,
            List<String> existingSkills,
            String experience,
            List<String> intendedTags
    ) {
        this.applicant = applicant;
        updateApplication(name, major, className, grade, contact, interestDirections, existingSkills, experience, intendedTags);
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AccountEntity getApplicant() { return applicant; }
    public String getName() { return name; }
    public String getMajor() { return major; }
    public String getClassName() { return className; }
    public String getGrade() { return grade; }
    public String getContact() { return contact; }
    public List<String> getInterestDirections() { return List.copyOf(interestDirections); }
    public List<String> getExistingSkills() { return List.copyOf(existingSkills); }
    public String getExperience() { return experience; }
    public List<String> getIntendedTags() { return List.copyOf(intendedTags); }
    public RecruitmentStage getStage() { return stage; }
    public UUID getInterviewerAccountId() { return interviewerAccountId; }
    public String getInterviewerName() { return interviewerName; }
    public Integer getInterviewScore() { return interviewScore; }
    public String getInterviewEvaluation() { return interviewEvaluation; }
    public List<String> getSuggestedTags() { return List.copyOf(suggestedTags); }
    public Boolean getInterviewPassed() { return interviewPassed; }
    public String getLinkedQuizId() { return linkedQuizId; }
    public UUID getConvertedMemberId() { return convertedMemberId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateApplication(
            String name,
            String major,
            String className,
            String grade,
            String contact,
            List<String> interestDirections,
            List<String> existingSkills,
            String experience,
            List<String> intendedTags
    ) {
        this.name = name;
        this.major = major;
        this.className = className;
        this.grade = grade;
        this.contact = contact;
        this.interestDirections = new ArrayList<>(interestDirections);
        this.existingSkills = new ArrayList<>(existingSkills);
        this.experience = experience;
        this.intendedTags = new ArrayList<>(intendedTags);
        this.updatedAt = Instant.now();
    }

    public void changeStage(RecruitmentStage stage) {
        this.stage = stage;
        this.updatedAt = Instant.now();
    }

    public void recordInterview(
            AccountEntity interviewer,
            Integer score,
            String evaluation,
            List<String> tags,
            Boolean passed
    ) {
        this.interviewerAccountId = interviewer.getId();
        this.interviewerName = interviewer.getUsername();
        this.interviewScore = score;
        this.interviewEvaluation = evaluation;
        this.suggestedTags = new ArrayList<>(tags);
        this.interviewPassed = passed;
        this.updatedAt = Instant.now();
    }

    public void setLinkedQuizId(String linkedQuizId) {
        this.linkedQuizId = linkedQuizId;
        this.updatedAt = Instant.now();
    }

    public void markConverted(UUID memberId) {
        this.convertedMemberId = memberId;
        this.updatedAt = Instant.now();
    }
}

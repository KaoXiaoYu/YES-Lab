package cn.yeslab.platform.identity.model;

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
@Table(name = "member_profiles")
public class MemberProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private AccountEntity account;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String memberCode;

    @Column(length = 100)
    private String major;

    @Column(name = "class_name", length = 100)
    private String className;

    @Column(length = 30)
    private String grade;

    @Column(length = 500)
    private String avatarUrl;

    @Column(length = 200)
    private String internalContact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MemberStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_skill_tags", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "tag", nullable = false, length = 80)
    private List<String> skillTags = new ArrayList<>();

    @Column(length = 160)
    private String headline;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String profileHtml = "<p>在这里介绍你的研究兴趣、项目经历与成长目标。</p>";

    @Column(nullable = false)
    private int totalPoints;

    private Integer currentRank;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_project_records", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "record_text", length = 500)
    private List<String> projectRecords = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_achievement_records", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "record_text", length = 500)
    private List<String> achievementRecords = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected MemberProfileEntity() {
    }

    public MemberProfileEntity(
            AccountEntity account,
            String name,
            String memberCode,
            String major,
            String className,
            String grade,
            String internalContact,
            MemberStatus status,
            List<String> skillTags
    ) {
        this.account = account;
        this.name = name;
        this.memberCode = memberCode;
        this.major = major;
        this.className = className;
        this.grade = grade;
        this.internalContact = internalContact;
        this.status = status;
        this.skillTags = new ArrayList<>(skillTags);
    }

    public UUID getId() { return id; }
    public AccountEntity getAccount() { return account; }
    public String getName() { return name; }
    public String getMemberCode() { return memberCode; }
    public String getMajor() { return major; }
    public String getClassName() { return className; }
    public String getGrade() { return grade; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getInternalContact() { return internalContact; }
    public MemberStatus getStatus() { return status; }
    public List<String> getSkillTags() { return List.copyOf(skillTags); }
    public String getHeadline() { return headline; }
    public String getProfileHtml() { return profileHtml; }
    public int getTotalPoints() { return totalPoints; }
    public Integer getCurrentRank() { return currentRank; }
    public List<String> getProjectRecords() { return List.copyOf(projectRecords); }
    public List<String> getAchievementRecords() { return List.copyOf(achievementRecords); }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateManagedFields(
            String name,
            String memberCode,
            String major,
            String className,
            String grade,
            String internalContact,
            MemberStatus status,
            List<String> skillTags
    ) {
        this.name = name;
        this.memberCode = memberCode;
        this.major = major;
        this.className = className;
        this.grade = grade;
        this.internalContact = internalContact;
        this.status = status;
        this.skillTags = new ArrayList<>(skillTags);
        this.updatedAt = Instant.now();
    }

    public void updateEditableFields(String avatarUrl, String internalContact, String headline, String profileHtml) {
        this.avatarUrl = avatarUrl;
        this.internalContact = internalContact;
        this.headline = headline;
        this.profileHtml = profileHtml;
        this.updatedAt = Instant.now();
    }
}

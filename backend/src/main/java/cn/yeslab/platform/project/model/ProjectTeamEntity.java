package cn.yeslab.platform.project.model;

import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "project_teams")
public class ProjectTeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String projectName;

    @Column(nullable = false, length = 120)
    private String teamName;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectStatus status;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_profile_id", nullable = false)
    private MemberProfileEntity leader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advisor_profile_id")
    private MemberProfileEntity advisor;

    @ManyToMany
    @JoinTable(
            name = "project_team_members",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "member_profile_id")
    )
    private Set<MemberProfileEntity> members = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "project_team_administrators",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "member_profile_id")
    )
    private Set<MemberProfileEntity> administrators = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "project_required_skill_tags", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "tag", nullable = false, length = 80)
    private List<String> requiredSkillTags = new ArrayList<>();

    private LocalDate startDate;

    private LocalDate endDate;

    @ElementCollection
    @CollectionTable(name = "project_stage_goals", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "goal", nullable = false, length = 500)
    private List<String> stageGoals = new ArrayList<>();

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String progressDescription;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String outcomes;

    @Column(length = 500)
    private String gitRepositoryUrl;

    @Column(length = 500)
    private String documentUrl;

    @Column(length = 120)
    private String coverStoredName;

    @Column(length = 255)
    private String coverOriginalName;

    @Column(length = 80)
    private String coverContentType;

    private Long coverSizeBytes;

    @Column(nullable = false)
    private boolean externallyVisible;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_account_id", nullable = false, updatable = false)
    private AccountEntity createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProjectTeamEntity() {
    }

    public ProjectTeamEntity(
            String projectName,
            String teamName,
            String description,
            ProjectType type,
            ProjectStatus status,
            MemberProfileEntity leader,
            MemberProfileEntity advisor,
            AccountEntity createdBy
    ) {
        this.projectName = projectName;
        this.teamName = teamName;
        this.description = description;
        this.type = type;
        this.status = status;
        this.leader = leader;
        this.advisor = advisor;
        this.createdBy = createdBy;
        this.members.add(leader);
    }

    public UUID getId() { return id; }
    public String getProjectName() { return projectName; }
    public String getTeamName() { return teamName; }
    public String getDescription() { return description; }
    public ProjectType getType() { return type; }
    public ProjectStatus getStatus() { return status; }
    public MemberProfileEntity getLeader() { return leader; }
    public MemberProfileEntity getAdvisor() { return advisor; }
    public Set<MemberProfileEntity> getMembers() { return Set.copyOf(members); }
    public Set<MemberProfileEntity> getAdministrators() { return Set.copyOf(administrators); }
    public List<String> getRequiredSkillTags() { return List.copyOf(requiredSkillTags); }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public List<String> getStageGoals() { return List.copyOf(stageGoals); }
    public String getProgressDescription() { return progressDescription; }
    public String getOutcomes() { return outcomes; }
    public String getGitRepositoryUrl() { return gitRepositoryUrl; }
    public String getDocumentUrl() { return documentUrl; }
    public String getCoverStoredName() { return coverStoredName; }
    public String getCoverOriginalName() { return coverOriginalName; }
    public String getCoverContentType() { return coverContentType; }
    public Long getCoverSizeBytes() { return coverSizeBytes; }
    public boolean isExternallyVisible() { return externallyVisible; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateDetails(
            String projectName,
            String description,
            ProjectType type,
            ProjectStatus status,
            MemberProfileEntity advisor,
            List<String> requiredSkillTags,
            LocalDate startDate,
            LocalDate endDate,
            List<String> stageGoals,
            String progressDescription,
            String outcomes,
            String gitRepositoryUrl,
            String documentUrl,
            boolean externallyVisible
    ) {
        this.projectName = projectName;
        this.description = description;
        this.type = type;
        this.status = status;
        this.advisor = advisor;
        this.requiredSkillTags = new ArrayList<>(requiredSkillTags);
        this.startDate = startDate;
        this.endDate = endDate;
        this.stageGoals = new ArrayList<>(stageGoals);
        this.progressDescription = progressDescription;
        this.outcomes = outcomes;
        this.gitRepositoryUrl = gitRepositoryUrl;
        this.documentUrl = documentUrl;
        this.externallyVisible = externallyVisible;
        this.updatedAt = Instant.now();
    }

    public void updateTeam(
            String teamName,
            MemberProfileEntity leader,
            Set<MemberProfileEntity> members,
            Set<MemberProfileEntity> administrators
    ) {
        this.teamName = teamName;
        this.leader = leader;
        this.members = new LinkedHashSet<>(members);
        this.members.add(leader);
        this.administrators = new LinkedHashSet<>(administrators);
        this.updatedAt = Instant.now();
    }

    public void updateCover(String storedName, String originalName, String contentType, long sizeBytes) {
        this.coverStoredName = storedName;
        this.coverOriginalName = originalName;
        this.coverContentType = contentType;
        this.coverSizeBytes = sizeBytes;
        this.updatedAt = Instant.now();
    }
}

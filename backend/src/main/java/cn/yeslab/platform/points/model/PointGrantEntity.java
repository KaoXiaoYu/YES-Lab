package cn.yeslab.platform.points.model;

import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.achievement.model.CompetitionEntity;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "point_grants")
public class PointGrantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointGrantType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PointSubcategory subcategory;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false)
    private LocalDate occurredOn;

    @Column(nullable = false)
    private int itemTotalPoints;

    @Column(nullable = false, unique = true, length = 190)
    private String sourceReference;

    @Column(nullable = false, length = 1000)
    private String evidenceUrl;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competition_id")
    private CompetitionEntity competition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectTeamEntity project;

    @Column(length = 36)
    private String sourceEntityReference;

    @Column(length = 180)
    private String sourceName;

    @Column(unique = true)
    private UUID requestKey;

    @Column(length = 64)
    private String requestFingerprint;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_account_id", nullable = false)
    private AccountEntity operator;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_grant_id", unique = true)
    private PointGrantEntity reversalOf;

    @OneToMany(mappedBy = "grant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<PointEntryEntity> entries = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PointGrantEntity() {
    }

    public PointGrantEntity(
            PointGrantType type,
            PointSubcategory subcategory,
            String title,
            LocalDate occurredOn,
            int itemTotalPoints,
            String sourceReference,
            String evidenceUrl,
            String description,
            AccountEntity operator,
            PointGrantEntity reversalOf
    ) {
        this.type = type;
        this.subcategory = subcategory;
        this.title = title;
        this.occurredOn = occurredOn;
        this.itemTotalPoints = itemTotalPoints;
        this.sourceReference = sourceReference;
        this.evidenceUrl = evidenceUrl;
        this.description = description;
        this.operator = operator;
        this.reversalOf = reversalOf;
    }

    public void addEntry(PointEntryEntity entry) {
        entries.add(entry);
    }

    public UUID getId() { return id; }
    public UUID getCompetitionId() { return competition == null ? null : competition.getId(); }
    public UUID getProjectId() { return project == null ? null : project.getId(); }
    public String getSourceEntityReference() { return sourceEntityReference; }
    public String getSourceName() { return sourceName; }
    public String getRequestFingerprint() { return requestFingerprint; }

    public void recordManualSource(CompetitionEntity competition, ProjectTeamEntity project,
                                   UUID requestKey, String fingerprint) {
        this.competition = competition;
        this.project = project;
        this.sourceEntityReference = competition != null ? competition.getId().toString()
                : project != null ? project.getId().toString() : null;
        this.sourceName = competition != null ? competition.getName()
                : project != null ? project.getProjectName() : null;
        this.requestKey = requestKey;
        this.requestFingerprint = fingerprint;
    }

    public void copySource(PointGrantEntity original) {
        competition = original.competition;
        project = original.project;
        sourceEntityReference = original.sourceEntityReference;
        sourceName = original.sourceName;
    }
    public PointGrantType getType() { return type; }
    public PointSubcategory getSubcategory() { return subcategory; }
    public String getTitle() { return title; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public int getItemTotalPoints() { return itemTotalPoints; }
    public String getSourceReference() { return sourceReference; }
    public String getEvidenceUrl() { return evidenceUrl; }
    public String getDescription() { return description; }
    public AccountEntity getOperator() { return operator; }
    public PointGrantEntity getReversalOf() { return reversalOf; }
    public List<PointEntryEntity> getEntries() { return List.copyOf(entries); }
    public Instant getCreatedAt() { return createdAt; }
}

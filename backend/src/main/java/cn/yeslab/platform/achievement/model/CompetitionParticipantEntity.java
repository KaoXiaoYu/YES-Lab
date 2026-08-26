package cn.yeslab.platform.achievement.model;

import cn.yeslab.platform.identity.model.MemberProfileEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "competition_participants")
public class CompetitionParticipantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "competition_id", nullable = false)
    private CompetitionEntity competition;

    @Column(nullable = false, length = 80)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_profile_id")
    private MemberProfileEntity linkedProfile;

    @Column(nullable = false)
    private boolean captain;

    @Column(nullable = false)
    private int displayOrder;

    protected CompetitionParticipantEntity() {
    }

    public CompetitionParticipantEntity(String displayName, MemberProfileEntity linkedProfile, boolean captain, int displayOrder) {
        this.displayName = displayName;
        this.linkedProfile = linkedProfile;
        this.captain = captain;
        this.displayOrder = displayOrder;
    }

    void attachTo(CompetitionEntity competition) { this.competition = competition; }
    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public MemberProfileEntity getLinkedProfile() { return linkedProfile; }
    public boolean isCaptain() { return captain; }
    public int getDisplayOrder() { return displayOrder; }
}

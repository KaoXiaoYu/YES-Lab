package cn.yeslab.platform.points.model;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "point_entries")
public class PointEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id", nullable = false)
    private PointGrantEntity grant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "member_profile_id", nullable = false)
    private MemberProfileEntity member;

    @Column(nullable = false)
    private int requestedPoints;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false, length = 500)
    private String contribution;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PointEntryEntity() {
    }

    public PointEntryEntity(
            PointGrantEntity grant,
            MemberProfileEntity member,
            int requestedPoints,
            int points,
            String contribution
    ) {
        this.grant = grant;
        this.member = member;
        this.requestedPoints = requestedPoints;
        this.points = points;
        this.contribution = contribution;
    }

    public UUID getId() { return id; }
    public PointGrantEntity getGrant() { return grant; }
    public MemberProfileEntity getMember() { return member; }
    public int getRequestedPoints() { return requestedPoints; }
    public int getPoints() { return points; }
    public String getContribution() { return contribution; }
    public Instant getCreatedAt() { return createdAt; }
}

package cn.yeslab.platform.recruitment.form.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "recruitment_form_versions", uniqueConstraints = @UniqueConstraint(name = "uk_form_version_number", columnNames = "version_number"))
public class RecruitmentFormVersionEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private long versionNumber;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String schemaJson;
    private UUID publishedByAccountId;
    @Column(nullable = false, length = 190) private String publishedBy;
    @Column(nullable = false) private Instant publishedAt;

    protected RecruitmentFormVersionEntity() { }
    public RecruitmentFormVersionEntity(long number, String schema, UUID actorId, String actor) {
        versionNumber = number;
        schemaJson = schema;
        publishedByAccountId = actorId;
        publishedBy = actor;
        publishedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public long getVersionNumber() { return versionNumber; }
    public String getSchemaJson() { return schemaJson; }
    public String getPublishedBy() { return publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
}

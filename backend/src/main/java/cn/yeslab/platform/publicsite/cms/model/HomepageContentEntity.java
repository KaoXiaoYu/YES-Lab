package cn.yeslab.platform.publicsite.cms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "homepage_content")
public class HomepageContentEntity {
    @Id
    private Long id;

    @Lob
    @Column(nullable = false)
    private String contentJson;

    @Column(nullable = false, length = 64)
    private String updatedBy;

    @Column(nullable = false)
    private Instant updatedAt;

    protected HomepageContentEntity() {
    }

    public HomepageContentEntity(String contentJson, String updatedBy) {
        this.id = 1L;
        update(contentJson, updatedBy);
    }

    public void update(String contentJson, String updatedBy) {
        this.contentJson = contentJson;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getContentJson() { return contentJson; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}

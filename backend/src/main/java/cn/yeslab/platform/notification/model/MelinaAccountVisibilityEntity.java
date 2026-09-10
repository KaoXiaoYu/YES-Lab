package cn.yeslab.platform.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "melina_account_visibility")
public class MelinaAccountVisibilityEntity {
    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false)
    private boolean visible;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected MelinaAccountVisibilityEntity() { }

    public MelinaAccountVisibilityEntity(UUID accountId, boolean visible) {
        this.accountId = accountId;
        this.visible = visible;
    }

    public UUID getAccountId() { return accountId; }
    public boolean isVisible() { return visible; }
}

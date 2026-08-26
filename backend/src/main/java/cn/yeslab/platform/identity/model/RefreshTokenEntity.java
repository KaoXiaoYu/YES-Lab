package cn.yeslab.platform.identity.model;

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
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private boolean rememberLogin;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(AccountEntity account, String tokenHash, boolean rememberLogin, Instant expiresAt) {
        this.account = account;
        this.tokenHash = tokenHash;
        this.rememberLogin = rememberLogin;
        this.expiresAt = expiresAt;
    }

    public AccountEntity getAccount() { return account; }
    public boolean isRememberLogin() { return rememberLogin; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public boolean isActiveAt(Instant instant) {
        return revokedAt == null && expiresAt.isAfter(instant) && account.isEnabled();
    }

    public void revoke(Instant instant) {
        if (revokedAt == null) revokedAt = instant;
    }
}

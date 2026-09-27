package cn.yeslab.platform.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "notification_mascot_preferences")
public class NotificationMascotPreferenceEntity {
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationMascot mascot;

    protected NotificationMascotPreferenceEntity() { }

    public NotificationMascotPreferenceEntity(UUID accountId, NotificationMascot mascot) {
        this.accountId = accountId;
        this.mascot = mascot;
    }

    public UUID getAccountId() { return accountId; }
    public NotificationMascot getMascot() { return mascot; }
}

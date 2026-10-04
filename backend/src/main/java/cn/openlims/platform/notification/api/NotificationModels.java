package cn.openlims.platform.notification.api;

import cn.openlims.platform.identity.model.Role;
import cn.openlims.platform.notification.model.NotificationMascot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NotificationModels {
    private NotificationModels() { }

    public record NotificationView(
            UUID id,
            String senderName,
            String type,
            String title,
            String summary,
            String targetPath,
            int aggregationCount,
            boolean read,
            Instant createdAt,
            Instant updatedAt
    ) { }

    public record InboxView(long unreadCount, List<NotificationView> messages, NotificationMascot mascot) { }

    public record VisibilityView(boolean visible, NotificationMascot mascot) { }

    public record AccountVisibilityView(
            UUID accountId,
            String username,
            String displayName,
            Role role,
            Boolean overrideVisible,
            boolean visible,
            NotificationMascot mascot
    ) { }

    public record AdminVisibilityView(Set<Role> visibleRoles, List<AccountVisibilityView> accounts) { }

    public record AccountVisibilityRequest(
            @NotNull UUID accountId,
            @NotNull Boolean visible
    ) { }

    public record AccountMascotRequest(@NotNull UUID accountId, @NotNull NotificationMascot mascot) { }

    public record AdminVisibilityRequest(
            @NotNull Set<Role> visibleRoles,
            @NotNull List<@NotNull @Valid AccountVisibilityRequest> overrides,
            List<@NotNull @Valid AccountMascotRequest> mascotOverrides
    ) { }
}

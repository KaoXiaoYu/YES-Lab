package cn.yeslab.platform.notification.api;

import java.time.Instant;
import java.util.List;
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

    public record InboxView(long unreadCount, List<NotificationView> messages) { }
}

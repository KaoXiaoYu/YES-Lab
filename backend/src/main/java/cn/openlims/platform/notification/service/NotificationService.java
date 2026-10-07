package cn.openlims.platform.notification.service;

import cn.openlims.platform.common.error.ApiException;
import cn.openlims.platform.identity.model.AccountEntity;
import cn.openlims.platform.identity.model.Role;
import cn.openlims.platform.identity.repository.AccountRepository;
import cn.openlims.platform.identity.service.AuthService;
import cn.openlims.platform.notification.api.NotificationModels;
import cn.openlims.platform.notification.model.NotificationEntity;
import cn.openlims.platform.notification.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final AccountRepository accounts;
    private final AuthService authService;
    private final MelinaVisibilityService visibility;

    public NotificationService(NotificationRepository notifications, AccountRepository accounts, AuthService authService,
                               MelinaVisibilityService visibility) {
        this.notifications = notifications;
        this.accounts = accounts;
        this.authService = authService;
        this.visibility = visibility;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public NotificationModels.InboxView inbox(Authentication authentication) {
        AccountEntity account = authService.requireAccount(authentication);
        var mascot = visibility.mascotFor(account.getId());
        List<NotificationModels.NotificationView> messages = notifications
                .findTop50ByRecipientIdOrderByCreatedAtDesc(account.getId()).stream()
                .map(item -> toView(item, mascot.getDisplayName())).toList();
        return new NotificationModels.InboxView(notifications.countByRecipientIdAndReadAtIsNull(account.getId()), messages, mascot);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public NotificationModels.InboxView markRead(Authentication authentication, UUID notificationId) {
        AccountEntity account = authService.requireAccount(authentication);
        NotificationEntity notification = notifications.findByIdAndRecipientId(notificationId, account.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "站内消息不存在"));
        notification.markRead();
        notifications.save(notification);
        return inbox(authentication);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public NotificationModels.InboxView markAllRead(Authentication authentication) {
        AccountEntity account = authService.requireAccount(authentication);
        List<NotificationEntity> unread = notifications.findByRecipientIdAndReadAtIsNull(account.getId());
        unread.forEach(NotificationEntity::markRead);
        notifications.saveAll(unread);
        return inbox(authentication);
    }

    @Transactional
    public void send(AccountEntity recipient, String type, String title, String summary, String targetPath) {
        notifications.save(new NotificationEntity(recipient, type, limit(title, 160), limit(summary, 500), targetPath, null));
    }

    @Transactional
    public void sendAggregated(AccountEntity recipient, String type, String title, String summary,
                               String targetPath, String groupKey) {
        NotificationEntity existing = notifications
                .findFirstByRecipientIdAndGroupKeyAndReadAtIsNullOrderByCreatedAtDesc(recipient.getId(), groupKey)
                .orElse(null);
        if (existing == null) {
            notifications.save(new NotificationEntity(recipient, type, limit(title.replace("{count}", "1"), 160),
                    limit(summary, 500), targetPath, groupKey));
        } else {
            int nextCount = existing.getAggregationCount() + 1;
            existing.aggregate(limit(title.replace("{count}", Integer.toString(nextCount)), 160), limit(summary, 500));
            notifications.save(existing);
        }
    }

    @Transactional
    public void broadcastDiscussionAnnouncement(String title, String summary, String targetPath) {
        List<NotificationEntity> messages = accounts.findByEnabledTrue().stream()
                .map(account -> new NotificationEntity(account, "DISCUSSION_ANNOUNCEMENT",
                        limit(title, 160), limit(summary, 500), targetPath, null))
                .toList();
        notifications.saveAll(messages);
    }

    @Transactional
    public void notifyAdminsNoInterviewSlots(UUID applicationId, String applicantName) {
        String groupKey = "NO_INTERVIEW_SLOTS:" + applicationId;
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        for (AccountEntity admin : accounts.findByRoleInAndEnabledTrue(List.of(Role.TEACHER, Role.CORE_STUDENT))) {
            if (!notifications.existsByRecipientIdAndGroupKeyAndCreatedAtAfter(admin.getId(), groupKey, cutoff)) {
                notifications.save(new NotificationEntity(admin, "INTERVIEW_SLOT_REQUIRED", "需要发布新的面试场次",
                        applicantName + " 已通过简历筛选，但目前没有可预约的面试场次。", "/admin/recruitment", groupKey));
            }
        }
    }

    private NotificationModels.NotificationView toView(NotificationEntity item, String senderName) {
        return new NotificationModels.NotificationView(item.getId(), senderName, item.getType(),
                HtmlUtils.htmlUnescape(item.getTitle()), HtmlUtils.htmlUnescape(item.getSummary()),
                item.getTargetPath(), item.getAggregationCount(), item.getReadAt() != null,
                item.getCreatedAt(), item.getUpdatedAt());
    }

    private static String limit(String value, int max) {
        String clean = value == null ? "" : value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}

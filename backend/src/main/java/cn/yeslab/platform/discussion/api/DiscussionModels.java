package cn.yeslab.platform.discussion.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DiscussionModels {
    private DiscussionModels() { }

    public record PostRequest(
            @NotBlank(message = "请输入标题") @Size(max = 160) String title,
            @NotBlank(message = "请输入讨论内容") @Size(max = 5000) String content,
            Boolean announcement
    ) { }

    public record ReplyRequest(
            @NotBlank(message = "请输入回复内容") @Size(max = 2000) String content
    ) { }

    public enum SortMode {
        NEWEST,
        OLDEST,
        ID_ASC,
        ID_DESC,
        MOST_LIKED,
        MOST_REPLIED
    }

    public record AuthorView(UUID accountId, UUID profileId, String name, String role, String avatarUrl) { }

    public record ReplyView(
            UUID id,
            long contentNumber,
            AuthorView author,
            String content,
            long likeCount,
            boolean likedByMe,
            boolean canEdit,
            boolean canDelete,
            Instant createdAt,
            Instant updatedAt
    ) { }

    public record PostView(
            UUID id,
            long contentNumber,
            AuthorView author,
            String title,
            String content,
            long likeCount,
            boolean likedByMe,
            boolean canEdit,
            boolean canDelete,
            boolean announcement,
            boolean pinned,
            boolean canPin,
            Instant pinnedAt,
            Instant createdAt,
            Instant updatedAt,
            List<ReplyView> replies
    ) { }

    public record ContributionView(
            long contentNumber,
            String type,
            UUID postId,
            String postTitle,
            String content,
            Instant createdAt
    ) { }
}

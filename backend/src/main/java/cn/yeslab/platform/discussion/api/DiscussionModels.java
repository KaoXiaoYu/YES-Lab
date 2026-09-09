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
            @NotBlank(message = "请输入讨论内容") @Size(max = 5000) String content
    ) { }

    public record ReplyRequest(
            @NotBlank(message = "请输入回复内容") @Size(max = 2000) String content
    ) { }

    public record AuthorView(UUID accountId, String name, String role) { }

    public record ReplyView(
            UUID id,
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
            AuthorView author,
            String title,
            String content,
            long likeCount,
            boolean likedByMe,
            boolean canEdit,
            boolean canDelete,
            Instant createdAt,
            Instant updatedAt,
            List<ReplyView> replies
    ) { }
}

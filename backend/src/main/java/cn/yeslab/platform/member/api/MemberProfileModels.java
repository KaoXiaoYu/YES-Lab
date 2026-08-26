package cn.yeslab.platform.member.api;

import cn.yeslab.platform.identity.model.MemberStatus;
import cn.yeslab.platform.identity.model.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MemberProfileModels {

    private MemberProfileModels() {
    }

    public record ProfileView(
            UUID id,
            String username,
            Role role,
            String name,
            String memberCode,
            String major,
            String className,
            String grade,
            String avatarUrl,
            String internalContact,
            MemberStatus status,
            List<String> skillTags,
            String headline,
            String profileHtml,
            int totalPoints,
            Integer currentRank,
            List<String> quizRecords,
            List<String> projectRecords,
            List<String> achievementRecords,
            Instant updatedAt
    ) {
    }

    public record UpdateProfileRequest(
            @Size(max = 500, message = "头像地址不能超过 500 个字符") String avatarUrl,
            @Size(max = 200, message = "联系方式不能超过 200 个字符") String internalContact,
            @Size(max = 160, message = "主页标语不能超过 160 个字符") String headline,
            @NotNull(message = "主页内容不能为空")
            @Size(max = 20000, message = "主页内容不能超过 20000 个字符")
            String profileHtml
    ) {
    }
}

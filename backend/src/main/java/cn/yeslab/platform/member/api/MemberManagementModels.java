package cn.yeslab.platform.member.api;

import cn.yeslab.platform.identity.model.MemberStatus;
import cn.yeslab.platform.identity.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MemberManagementModels {

    private MemberManagementModels() {
    }

    public record PublicProfileView(
            UUID id,
            Role role,
            String name,
            String major,
            String className,
            String grade,
            String avatarUrl,
            MemberStatus status,
            List<String> skillTags,
            String headline,
            String profileHtml,
            Integer totalPoints,
            Integer currentRank,
            List<String> projectRecords,
            List<String> achievementRecords,
            Instant updatedAt
    ) {
    }

    public record UpdateMemberRequest(
            @NotBlank(message = "姓名不能为空")
            @Size(max = 80, message = "姓名不能超过 80 个字符") String name,
            @NotBlank(message = "成员编号不能为空")
            @Pattern(regexp = "[A-Za-z0-9._-]{2,64}", message = "成员编号仅支持字母、数字、点、下划线和短横线") String memberCode,
            @NotNull(message = "请选择成员角色") Role role,
            @Size(max = 100, message = "专业不能超过 100 个字符") String major,
            @Size(max = 100, message = "班级不能超过 100 个字符") String className,
            @Size(max = 30, message = "年级不能超过 30 个字符") String grade,
            @Size(max = 200, message = "联系方式不能超过 200 个字符") String internalContact,
            @NotNull(message = "请选择成员状态") MemberStatus status,
            @NotEmpty(message = "至少需要一个能力标签")
            @Size(max = 12, message = "能力标签不能超过 12 个") List<@NotBlank @Size(max = 80) String> skillTags
    ) {
    }
}

package cn.yeslab.platform.identity.api;

import cn.yeslab.platform.identity.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuthModels {

    private AuthModels() {
    }

    public record LoginRequest(
            @NotBlank(message = "请输入账号") String username,
            @NotBlank(message = "请输入密码") String password
    ) {
    }

    public record RegisterRequest(
            @NotBlank(message = "请输入账号")
            @Pattern(regexp = "[A-Za-z0-9_.-]{4,32}", message = "账号需为 4—32 位字母、数字、点、下划线或短横线")
            String username,
            @NotBlank(message = "请输入密码")
            @Size(min = 10, max = 72, message = "密码长度需为 10—72 位")
            String password
    ) {
    }

    public record AccountView(
            UUID id,
            String username,
            Role role,
            List<String> permissions,
            boolean systemAdmin,
            boolean memberProfileAvailable,
            boolean recruitmentApplicationAvailable,
            String displayName,
            String avatarUrl
    ) {
    }

    public record AuthResponse(
            String accessToken,
            String tokenType,
            Instant expiresAt,
            AccountView account
    ) {
    }
}

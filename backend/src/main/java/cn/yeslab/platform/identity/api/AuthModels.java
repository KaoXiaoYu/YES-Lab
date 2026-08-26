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
            @NotBlank(message = "请输入密码") String password,
            Boolean rememberMe
    ) {
    }

    public record RegisterRequest(
            @NotBlank(message = "请输入邮箱或手机号码")
            @Size(max = 190, message = "邮箱或手机号码不能超过 190 位")
            @Pattern(
                    regexp = "(?i)^(?:[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+|\\+?[1-9](?:[ -]?\\d){6,14})$",
                    message = "请输入有效的邮箱或手机号码"
            )
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

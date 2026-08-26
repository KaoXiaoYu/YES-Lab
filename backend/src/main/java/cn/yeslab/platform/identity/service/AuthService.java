package cn.yeslab.platform.identity.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.api.AuthModels;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.RefreshTokenEntity;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.AccountRepository;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.repository.RefreshTokenRepository;
import cn.yeslab.platform.recruitment.repository.RecruitmentApplicationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private final AccountRepository accounts;
    private final MemberProfileRepository profiles;
    private final RecruitmentApplicationRepository applications;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokens;
    private final RefreshTokenRepository refreshTokens;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration sessionRefreshTtl;
    private final Duration rememberedRefreshTtl;

    public AuthService(
            AccountRepository accounts,
            MemberProfileRepository profiles,
            RecruitmentApplicationRepository applications,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenService tokens,
            RefreshTokenRepository refreshTokens,
            @Value("${yeslab.security.refresh-token.session-ttl}") Duration sessionRefreshTtl,
            @Value("${yeslab.security.refresh-token.remembered-ttl}") Duration rememberedRefreshTtl
    ) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.applications = applications;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.sessionRefreshTtl = sessionRefreshTtl;
        this.rememberedRefreshTtl = rememberedRefreshTtl;
    }

    @Transactional
    public AuthSession login(AuthModels.LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
            );
        } catch (AuthenticationException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }

        AccountEntity account = accounts.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "账号或密码错误"));
        return createSession(account, Boolean.TRUE.equals(request.rememberMe()));
    }

    @Transactional
    public AuthSession register(AuthModels.RegisterRequest request) {
        String normalizedUsername = request.username().trim().toLowerCase();
        if (accounts.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new ApiException(HttpStatus.CONFLICT, "该账号已被注册");
        }
        AccountEntity account = accounts.save(new AccountEntity(
                normalizedUsername,
                passwordEncoder.encode(request.password()),
                Role.VISITOR
        ));
        return createSession(account, false);
    }

    @Transactional
    public AuthSession refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        RefreshTokenEntity current = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录"));
        if (!current.isActiveAt(now)) {
            current.revoke(now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        current.revoke(now);
        return createSession(current.getAccount(), current.isRememberLogin());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> token.revoke(Instant.now()));
    }

    @Transactional(readOnly = true)
    public AuthModels.AccountView current(Authentication authentication) {
        return toView(requireAccount(authentication));
    }

    @Transactional(readOnly = true)
    public AccountEntity requireAccount(Authentication authentication) {
        try {
            UUID accountId = UUID.fromString(authentication.getName());
            return accounts.findById(accountId)
                    .filter(AccountEntity::isEnabled)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "账号不可用"));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "登录状态无效");
        }
    }

    private AuthSession createSession(AccountEntity account, boolean rememberLogin) {
        JwtTokenService.IssuedToken token = tokens.issue(account);
        String rawRefreshToken = newRefreshToken();
        Duration refreshTtl = rememberLogin ? rememberedRefreshTtl : sessionRefreshTtl;
        Instant refreshExpiresAt = Instant.now().plus(refreshTtl);
        refreshTokens.deleteByExpiresAtBefore(Instant.now().minus(Duration.ofDays(1)));
        refreshTokens.save(new RefreshTokenEntity(account, hash(rawRefreshToken), rememberLogin, refreshExpiresAt));
        AuthModels.AuthResponse response = new AuthModels.AuthResponse(
                token.value(), "Bearer", token.expiresAt(), toView(account)
        );
        return new AuthSession(response, rawRefreshToken, refreshTtl, rememberLogin);
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private AuthModels.AccountView toView(AccountEntity account) {
        var profile = profiles.findByAccountId(account.getId());
        return new AuthModels.AccountView(
                account.getId(),
                account.getUsername(),
                account.getRole(),
                account.getRole().permissions().stream().map(Enum::name).sorted().toList(),
                account.getRole().isSystemAdmin(),
                profile.isPresent(),
                applications.findByApplicantId(account.getId()).isPresent(),
                profile.map(memberProfile -> memberProfile.getName()).orElse(account.getUsername()),
                profile.map(memberProfile -> memberProfile.getAvatarUrl()).orElse(null)
        );
    }

    public record AuthSession(
            AuthModels.AuthResponse response,
            String refreshToken,
            Duration refreshTtl,
            boolean rememberLogin
    ) {
    }
}

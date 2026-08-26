package cn.yeslab.platform.identity.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.api.AuthModels;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.AccountRepository;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.recruitment.repository.RecruitmentApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final AccountRepository accounts;
    private final MemberProfileRepository profiles;
    private final RecruitmentApplicationRepository applications;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokens;

    public AuthService(
            AccountRepository accounts,
            MemberProfileRepository profiles,
            RecruitmentApplicationRepository applications,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenService tokens
    ) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.applications = applications;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public AuthModels.AuthResponse login(AuthModels.LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
            );
        } catch (AuthenticationException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }

        AccountEntity account = accounts.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "账号或密码错误"));
        return createResponse(account);
    }

    @Transactional
    public AuthModels.AuthResponse register(AuthModels.RegisterRequest request) {
        String normalizedUsername = request.username().trim().toLowerCase();
        if (accounts.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new ApiException(HttpStatus.CONFLICT, "该账号已被注册");
        }
        AccountEntity account = accounts.save(new AccountEntity(
                normalizedUsername,
                passwordEncoder.encode(request.password()),
                Role.VISITOR
        ));
        return createResponse(account);
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

    private AuthModels.AuthResponse createResponse(AccountEntity account) {
        JwtTokenService.IssuedToken token = tokens.issue(account);
        return new AuthModels.AuthResponse(token.value(), "Bearer", token.expiresAt(), toView(account));
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
}

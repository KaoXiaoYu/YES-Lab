package cn.yeslab.platform.notification.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.AccountRepository;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.notification.api.NotificationModels;
import cn.yeslab.platform.notification.model.MelinaAccountVisibilityEntity;
import cn.yeslab.platform.notification.model.MelinaRoleVisibilityEntity;
import cn.yeslab.platform.notification.repository.MelinaAccountVisibilityRepository;
import cn.yeslab.platform.notification.repository.MelinaRoleVisibilityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MelinaVisibilityService {
    private final MelinaRoleVisibilityRepository roleVisibility;
    private final MelinaAccountVisibilityRepository accountVisibility;
    private final AccountRepository accounts;
    private final MemberProfileRepository profiles;
    private final AuthService authService;

    public MelinaVisibilityService(MelinaRoleVisibilityRepository roleVisibility,
                                   MelinaAccountVisibilityRepository accountVisibility,
                                   AccountRepository accounts, MemberProfileRepository profiles,
                                   AuthService authService) {
        this.roleVisibility = roleVisibility;
        this.accountVisibility = accountVisibility;
        this.accounts = accounts;
        this.profiles = profiles;
        this.authService = authService;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public NotificationModels.VisibilityView visibility(Authentication authentication) {
        AccountEntity account = authService.requireAccount(authentication);
        return new NotificationModels.VisibilityView(isVisible(account));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @Transactional(readOnly = true)
    public NotificationModels.AdminVisibilityView adminView() {
        return buildAdminView();
    }

    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public NotificationModels.AdminVisibilityView update(NotificationModels.AdminVisibilityRequest request) {
        Set<Role> selectedRoles = request.visibleRoles().isEmpty()
                ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(request.visibleRoles());
        if (!Arrays.asList(Role.values()).containsAll(selectedRoles)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "梅琳娜展示角色无效");
        }

        Set<UUID> seen = new HashSet<>();
        Map<UUID, AccountEntity> enabledAccounts = accounts.findByEnabledTrue().stream()
                .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        List<MelinaAccountVisibilityEntity> overrides = request.overrides().stream().map(item -> {
            if (!seen.add(item.accountId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "同一账号不能重复设置");
            }
            if (!enabledAccounts.containsKey(item.accountId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "展示例外账号不存在或已停用");
            }
            return new MelinaAccountVisibilityEntity(item.accountId(), item.visible());
        }).toList();

        roleVisibility.saveAll(Arrays.stream(Role.values())
                .map(role -> new MelinaRoleVisibilityEntity(role, selectedRoles.contains(role))).toList());
        accountVisibility.deleteAllInBatch();
        accountVisibility.saveAll(overrides);
        return buildAdminView();
    }

    private NotificationModels.AdminVisibilityView buildAdminView() {
        Set<Role> visibleRoles = roleVisibility.findAll().stream()
                .filter(MelinaRoleVisibilityEntity::isVisible)
                .map(MelinaRoleVisibilityEntity::getRole)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
        Map<UUID, Boolean> overrides = accountVisibility.findAll().stream()
                .collect(Collectors.toMap(MelinaAccountVisibilityEntity::getAccountId,
                        MelinaAccountVisibilityEntity::isVisible));
        List<NotificationModels.AccountVisibilityView> accountViews = accounts.findByEnabledTrue().stream()
                .sorted(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(AccountEntity::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(account -> new NotificationModels.AccountVisibilityView(account.getId(), account.getUsername(),
                        displayName(account), account.getRole(), overrides.get(account.getId()), isVisible(account)))
                .toList();
        return new NotificationModels.AdminVisibilityView(Set.copyOf(visibleRoles), accountViews);
    }

    private boolean isVisible(AccountEntity account) {
        return accountVisibility.findById(account.getId())
                .map(MelinaAccountVisibilityEntity::isVisible)
                .orElseGet(() -> roleVisibility.findById(account.getRole())
                        .map(MelinaRoleVisibilityEntity::isVisible).orElse(true));
    }

    private String displayName(AccountEntity account) {
        return profiles.findByAccountId(account.getId()).map(MemberProfileEntity::getName)
                .orElse(account.getUsername());
    }
}

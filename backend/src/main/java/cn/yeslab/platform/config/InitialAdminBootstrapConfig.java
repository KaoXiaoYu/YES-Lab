package cn.yeslab.platform.config;

import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.model.MemberStatus;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.AccountRepository;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Creates one real production administrator without enabling the demo data set.
 * The bootstrap is idempotent: an existing username is never modified or promoted.
 */
@Component
@ConditionalOnProperty(name = "yeslab.security.initial-admin.enabled", havingValue = "true")
public class InitialAdminBootstrapConfig implements ApplicationRunner {

    private final AccountRepository accounts;
    private final MemberProfileRepository profiles;
    private final PasswordEncoder passwords;
    private final String username;
    private final String password;
    private final String displayName;
    private final String memberCode;

    public InitialAdminBootstrapConfig(
            AccountRepository accounts,
            MemberProfileRepository profiles,
            PasswordEncoder passwords,
            @Value("${yeslab.security.initial-admin.username:admin}") String username,
            @Value("${yeslab.security.initial-admin.password:}") String password,
            @Value("${yeslab.security.initial-admin.display-name:系统管理员}") String displayName,
            @Value("${yeslab.security.initial-admin.member-code:T-001}") String memberCode
    ) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.passwords = passwords;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.displayName = displayName == null ? "" : displayName.trim();
        this.memberCode = memberCode == null ? "" : memberCode.trim();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validateConfiguration();
        if (accounts.findByUsernameIgnoreCase(username).isPresent()) {
            return;
        }
        if (profiles.findByMemberCodeIgnoreCase(memberCode).isPresent()) {
            throw new IllegalStateException("初始管理员内部编号已被占用，请更换 YESLAB_INITIAL_ADMIN_MEMBER_CODE");
        }

        AccountEntity account = accounts.save(
                new AccountEntity(username, passwords.encode(password), Role.TEACHER)
        );
        MemberProfileEntity profile = new MemberProfileEntity(
                account,
                displayName,
                memberCode,
                null,
                null,
                null,
                null,
                MemberStatus.OFFICIAL,
                List.of("系统管理")
        );
        profile.updateEditableFields(
                null,
                null,
                "YES Lab 系统管理员",
                "<p>负责 YES Lab 平台与实验室事务管理。</p>"
        );
        profiles.save(profile);
    }

    private void validateConfiguration() {
        if (username.isBlank() || username.length() > 64) {
            throw new IllegalStateException("YESLAB_INITIAL_ADMIN_USERNAME 长度必须为 1—64 位");
        }
        if (password.length() < 16 || password.length() > 72) {
            throw new IllegalStateException("YESLAB_INITIAL_ADMIN_PASSWORD 长度必须为 16—72 位");
        }
        if (displayName.isBlank() || displayName.length() > 80) {
            throw new IllegalStateException("YESLAB_INITIAL_ADMIN_DISPLAY_NAME 长度必须为 1—80 位");
        }
        if (memberCode.isBlank() || memberCode.length() > 64) {
            throw new IllegalStateException("YESLAB_INITIAL_ADMIN_MEMBER_CODE 长度必须为 1—64 位");
        }
    }
}

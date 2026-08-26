package cn.yeslab.platform.config;

import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.AccountRepository;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialAdminBootstrapConfigTests {

    @Test
    void createsTeacherAdministratorWithoutDemoAccounts() throws Exception {
        AccountRepository accounts = mock(AccountRepository.class);
        MemberProfileRepository profiles = mock(MemberProfileRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(accounts.findByUsernameIgnoreCase("teacher")).thenReturn(Optional.empty());
        when(profiles.findByMemberCodeIgnoreCase("T-001")).thenReturn(Optional.empty());
        when(passwords.encode("a-secure-initial-password")).thenReturn("encoded-password");
        when(accounts.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InitialAdminBootstrapConfig bootstrap = new InitialAdminBootstrapConfig(
                accounts,
                profiles,
                passwords,
                "teacher",
                "a-secure-initial-password",
                "汤洪大王",
                "T-001"
        );
        bootstrap.run(new DefaultApplicationArguments());

        verify(accounts).save(org.mockito.ArgumentMatchers.argThat(account ->
                account.getUsername().equals("teacher")
                        && account.getPasswordHash().equals("encoded-password")
                        && account.getRole() == Role.TEACHER
        ));
        verify(profiles).save(org.mockito.ArgumentMatchers.argThat(profile ->
                profile.getName().equals("汤洪大王")
                        && profile.getMemberCode().equals("T-001")
                        && profile.getSkillTags().equals(java.util.List.of("系统管理"))
        ));
    }

    @Test
    void neverChangesAnExistingUsername() throws Exception {
        AccountRepository accounts = mock(AccountRepository.class);
        MemberProfileRepository profiles = mock(MemberProfileRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        AccountEntity existing = new AccountEntity("teacher", "existing-hash", Role.VISITOR);
        when(accounts.findByUsernameIgnoreCase("teacher")).thenReturn(Optional.of(existing));

        InitialAdminBootstrapConfig bootstrap = new InitialAdminBootstrapConfig(
                accounts,
                profiles,
                passwords,
                "teacher",
                "a-secure-initial-password",
                "汤洪大王",
                "T-001"
        );
        bootstrap.run(new DefaultApplicationArguments());

        assertEquals(Role.VISITOR, existing.getRole());
        verify(accounts, never()).save(any());
        verify(profiles, never()).save(any());
    }

    @Test
    void rejectsWeakInitialPassword() {
        InitialAdminBootstrapConfig bootstrap = new InitialAdminBootstrapConfig(
                mock(AccountRepository.class),
                mock(MemberProfileRepository.class),
                mock(PasswordEncoder.class),
                "teacher",
                "too-short",
                "汤洪大王",
                "T-001"
        );

        assertThrows(
                IllegalStateException.class,
                () -> bootstrap.run(new DefaultApplicationArguments())
        );
    }
}

package cn.yeslab.platform.config;

import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.model.MemberStatus;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.AccountRepository;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.project.model.ProjectStatus;
import cn.yeslab.platform.project.model.ProjectTeamEntity;
import cn.yeslab.platform.project.model.ProjectType;
import cn.yeslab.platform.project.repository.ProjectTeamRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDate;
import java.util.LinkedHashSet;

@Component
@ConditionalOnProperty(name = "yeslab.security.bootstrap.enabled", havingValue = "true")
public class BootstrapDataConfig implements ApplicationRunner {

    private final AccountRepository accounts;
    private final MemberProfileRepository profiles;
    private final PasswordEncoder passwords;
    private final ProjectTeamRepository projects;
    private final String teacherPassword;
    private final String corePassword;
    private final String memberPassword;

    public BootstrapDataConfig(
            AccountRepository accounts,
            MemberProfileRepository profiles,
            ProjectTeamRepository projects,
            PasswordEncoder passwords,
            @Value("${yeslab.security.bootstrap.teacher-password}") String teacherPassword,
            @Value("${yeslab.security.bootstrap.core-password}") String corePassword,
            @Value("${yeslab.security.bootstrap.member-password}") String memberPassword
    ) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.projects = projects;
        this.passwords = passwords;
        this.teacherPassword = teacherPassword;
        this.corePassword = corePassword;
        this.memberPassword = memberPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createMemberAccount(
                "teacher", teacherPassword, Role.TEACHER, "汤洪大王", "T-001",
                null, null, null, "teacher@yes-lab.internal", List.of("研究指导", "人才培养")
        );
        createMemberAccount(
                "core", corePassword, Role.CORE_STUDENT, "范桌轩大王", "S-CORE-001",
                "计算机科学", "计科 2301", "2023", "core@yes-lab.internal", List.of("无人机系统", "工程实现")
        );
        createMemberAccount(
                "member", memberPassword, Role.MEMBER, "范桌轩大王", "S-001",
                "人工智能", "人工智能 2401", "2024", "member@yes-lab.internal", List.of("具身智能")
        );
        createDemoProject();
    }

    private void createMemberAccount(
            String username,
            String password,
            Role role,
            String name,
            String memberCode,
            String major,
            String className,
            String grade,
            String contact,
            List<String> skillTags
    ) {
        AccountEntity account = accounts.findByUsernameIgnoreCase(username)
                .orElseGet(() -> accounts.save(new AccountEntity(username, passwords.encode(password), role)));
        if (profiles.findByAccountId(account.getId()).isEmpty()) {
            MemberProfileEntity profile = new MemberProfileEntity(
                    account, name, memberCode, major, className, grade, contact,
                    MemberStatus.OFFICIAL, skillTags
            );
            profile.updateEditableFields(
                    contact,
                    role == Role.TEACHER ? "指导真实世界中的智能系统研究" : "让工程能力在真实项目中生长",
                    role == Role.TEACHER
                            ? "<p>负责实验室研究方向、项目实践与人才培养指导。</p>"
                            : "<p>关注无人系统与具身智能，持续记录项目、竞赛和研究成长。</p>"
            );
            profiles.save(profile);
        }
    }

    private void createDemoProject() {
        if (projects.count() > 0) return;
        MemberProfileEntity teacher = profiles.findByMemberCodeIgnoreCase("T-001").orElse(null);
        MemberProfileEntity core = profiles.findByMemberCodeIgnoreCase("S-CORE-001").orElse(null);
        MemberProfileEntity member = profiles.findByMemberCodeIgnoreCase("S-001").orElse(null);
        if (teacher == null || core == null || member == null) return;

        ProjectTeamEntity project = new ProjectTeamEntity(
                "无人机与机器狗空地协同系统",
                "AIR-GROUND / 01",
                "连接空中视野与地面行动能力，验证异构机器人协同感知、任务分配和真实环境执行。",
                ProjectType.RESEARCH,
                ProjectStatus.ACTIVE,
                core,
                teacher,
                teacher.getAccount()
        );
        project.updateDetails(
                project.getProjectName(),
                project.getDescription(),
                ProjectType.RESEARCH,
                ProjectStatus.ACTIVE,
                teacher,
                List.of("无人机系统", "机器人控制", "多智能体协同"),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 12, 31),
                List.of("完成空地平台通信链路", "实现协同感知原型", "完成真实场景联调"),
                "已完成平台选型与通信协议设计，正在开展协同定位和任务分配验证。",
                "阶段成果将在原型、竞赛记录和技术文档通过审核后持续补充。",
                "https://github.com",
                null,
                true
        );
        project.updateTeam(
                project.getTeamName(),
                core,
                new LinkedHashSet<>(List.of(core, member)),
                new LinkedHashSet<>()
        );
        projects.save(project);
    }
}

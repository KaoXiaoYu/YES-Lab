package cn.yeslab.platform.identity;

import cn.yeslab.platform.identity.model.Permission;
import cn.yeslab.platform.identity.model.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionTests {

    @Test
    void teacherAndCoreStudentShareSystemAdministrationPermissions() {
        assertThat(Role.TEACHER.permissions()).isEqualTo(Role.CORE_STUDENT.permissions());
        assertThat(Role.TEACHER.permissions()).contains(
                Permission.SYSTEM_ADMIN,
                Permission.MEMBER_MANAGE,
                Permission.RECRUITMENT_MANAGE,
                Permission.TAG_MANAGE,
                Permission.QUIZ_MANAGE,
                Permission.POINTS_MANAGE,
                Permission.PROJECT_MANAGE,
                Permission.ACHIEVEMENT_MANAGE,
                Permission.CONTENT_MANAGE
        );
    }

    @Test
    void memberAndVisitorPermissionsStaySeparated() {
        assertThat(Role.MEMBER.permissions()).contains(Permission.PROFILE_SELF_EDIT, Permission.QUIZ_PARTICIPATE, Permission.QUESTION_WRITE);
        assertThat(Role.MEMBER.permissions()).doesNotContain(Permission.RECRUITMENT_MANAGE);
        assertThat(Role.VISITOR.permissions()).containsExactlyInAnyOrder(Permission.RECRUITMENT_SELF_EDIT, Permission.RECRUITMENT_SELF_VIEW);
        assertThat(Role.VISITOR.permissions()).doesNotContain(Permission.PROFILE_SELF_EDIT);
    }
}

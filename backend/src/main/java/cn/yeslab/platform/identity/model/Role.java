package cn.yeslab.platform.identity.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum Role {
    TEACHER(adminPermissions()),
    CORE_STUDENT(adminPermissions()),
    MEMBER(EnumSet.of(
            Permission.PROFILE_SELF_EDIT,
            Permission.QUIZ_PARTICIPATE,
            Permission.QUESTION_WRITE
    )),
    VISITOR(EnumSet.of(
            Permission.RECRUITMENT_SELF_EDIT,
            Permission.RECRUITMENT_SELF_VIEW
    ));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean isSystemAdmin() {
        return this == TEACHER || this == CORE_STUDENT;
    }

    private static EnumSet<Permission> adminPermissions() {
        return EnumSet.of(
                Permission.SYSTEM_ADMIN,
                Permission.MEMBER_MANAGE,
                Permission.RECRUITMENT_MANAGE,
                Permission.TAG_MANAGE,
                Permission.QUIZ_MANAGE,
                Permission.POINTS_MANAGE,
                Permission.PROJECT_MANAGE,
                Permission.ACHIEVEMENT_MANAGE,
                Permission.CONTENT_MANAGE,
                Permission.PROFILE_SELF_EDIT,
                Permission.QUIZ_PARTICIPATE,
                Permission.QUESTION_WRITE
        );
    }
}

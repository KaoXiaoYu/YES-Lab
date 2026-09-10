package cn.yeslab.platform.notification.model;

import cn.yeslab.platform.identity.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "melina_role_visibility")
public class MelinaRoleVisibilityEntity {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Role role;

    @Column(nullable = false)
    private boolean visible;

    protected MelinaRoleVisibilityEntity() { }

    public MelinaRoleVisibilityEntity(Role role, boolean visible) {
        this.role = role;
        this.visible = visible;
    }

    public Role getRole() { return role; }
    public boolean isVisible() { return visible; }
}

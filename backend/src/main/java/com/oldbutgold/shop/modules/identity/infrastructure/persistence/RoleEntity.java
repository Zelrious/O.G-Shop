package com.oldbutgold.shop.modules.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class RoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Short id;

    @Column(name = "role_name", nullable = false, unique = true)
    private String roleName;

    protected RoleEntity() {
    }

    public Short getId() {
        return id;
    }

    public String getRoleName() {
        return roleName;
    }
}

package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "access_role_permissions")
@Getter
@Setter
public class AccessRolePermission extends BaseEntity {

    @Column(nullable = false)
    private UUID roleId;

    @Column(nullable = false, length = 40)
    private String permissionCode;
}

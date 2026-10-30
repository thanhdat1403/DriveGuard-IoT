
package com.driveguard.backend.user.entity;

import com.driveguard.backend.role.entity.RoleEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // Khóa ngoại role_id -> roles.id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @Column(name = "username", nullable = false,
            length = 50, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false,
            length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false,
            length = 100)
    private String fullName;

    @Column(name = "email", length = 255, unique = true)
    private String email;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false,
            insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}

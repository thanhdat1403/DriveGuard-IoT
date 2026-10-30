
package com.driveguard.backend.role.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class RoleEntity {

    // id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // name VARCHAR(30) NOT NULL UNIQUE
    @Column(
            name = "name",
            nullable = false,
            unique = true,
            length = 30
    )
    private String name;

    // description VARCHAR(255) DEFAULT NULL
    @Column(name = "description", length = 255)
    private String description;

    // created_at DATETIME NOT NULL
    // DEFAULT CURRENT_TIMESTAMP
    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;
}


package com.driveguard.backend.role.repository;

import com.driveguard.backend.role.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    // Tìm vai trò theo tên: ADMIN, SUPERVISOR,
    // TECHNICIAN, DRIVER, VIEWER
    Optional<RoleEntity> findByName(String name);

    // Kiểm tra tên vai trò đã tồn tại hay chưa
    boolean existsByName(String name);
}

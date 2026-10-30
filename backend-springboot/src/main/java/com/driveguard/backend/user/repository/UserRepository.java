
package com.driveguard.backend.user.repository;

import com.driveguard.backend.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    // Tìm tài khoản theo username
    Optional<UserEntity> findByUsername(String username);

    // Tìm tài khoản đang hoạt động theo username
    Optional<UserEntity> findByUsernameAndActiveTrue(String username);

    // Kiểm tra username đã tồn tại chưa
    boolean existsByUsername(String username);

    // Kiểm tra email đã tồn tại chưa
    boolean existsByEmail(String email);
}

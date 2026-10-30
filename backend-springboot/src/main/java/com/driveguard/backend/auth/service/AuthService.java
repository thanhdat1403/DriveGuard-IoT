
package com.driveguard.backend.auth.service;

import com.driveguard.backend.auth.dto.request.LoginRequest;
import com.driveguard.backend.auth.dto.response.LoginResponse;
import com.driveguard.backend.user.entity.UserEntity;
import com.driveguard.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;


    public LoginResponse login(LoginRequest request) {

        String username = request.username().trim();
        String password = request.password();

        // 1. Tim tai khoan trong MariaDB
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Ten dang nhap hoac mat khau khong chinh xac"
                ));

        // 2. Kiem tra mat khau BCrypt
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Ten dang nhap hoac mat khau khong chinh xac"
            );
        }

        // 3. Kiem tra tai khoan co bi vo hieu hoa khong
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tai khoan da bi vo hieu hoa"
            );
        }

        // 4. Lay vai tro cua nguoi dung
        String role = user.getRole().getName();

        // 5. Tao JWT
        String accessToken = jwtService.generateToken(
                user.getId(),
                role
        );

        // 6. Tao thong tin nguoi dung tra ve
        LoginResponse.UserInfo userInfo =
                new LoginResponse.UserInfo(
                        user.getId(),
                        user.getUsername(),
                        user.getFullName(),
                        user.getEmail(),
                        role
                );

        // 7. Tao du lieu dang nhap
        LoginResponse.LoginData loginData =
                new LoginResponse.LoginData(
                        accessToken,
                        "Bearer",
                        "1h",
                        userInfo
                );

        // 8. Tra ket qua cho Frontend
        return new LoginResponse(
                true,
                "Dang nhap thanh cong",
                loginData
        );
    }


    public LoginResponse.UserInfo getCurrentUser(Long userId) {

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Tai khoan khong ton tai"
                ));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tai khoan da bi vo hieu hoa"
            );
        }

        return new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getName()
        );
    }

}

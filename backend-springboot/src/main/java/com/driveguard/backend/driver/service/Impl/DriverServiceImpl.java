package com.driveguard.backend.driver.service.Impl;

import com.driveguard.backend.driver.dto.request.DriverCreateRequest;
import com.driveguard.backend.driver.dto.response.DriverResponse;
import com.driveguard.backend.driver.entity.DriverEntity;
import com.driveguard.backend.driver.repository.DriverRepository;
import com.driveguard.backend.driver.service.DriverService;
import com.driveguard.backend.role.entity.RoleEntity;
import com.driveguard.backend.role.repository.RoleRepository;
import com.driveguard.backend.user.entity.UserEntity;
import com.driveguard.backend.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Tạo User và Driver trong cùng một transaction.
     */
    @Override
    @Transactional
    public DriverResponse createDriver(
            DriverCreateRequest request
    ) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dữ liệu tạo tài xế không được để trống"
            );
        }

        // 1. Chuẩn hóa dữ liệu đầu vào
        String username = requireText(
                request.username(),
                "Username"
        ).toLowerCase(Locale.ROOT);

        String fullName = requireText(
                request.fullName(),
                "Họ tên"
        );

        String driverCode = requireText(
                request.driverCode(),
                "Mã tài xế"
        ).toUpperCase(Locale.ROOT);

        String email = normalizeEmail(request.email());
        String phone = normalizeOptional(request.phone());
        String licenseNumber = normalizeOptional(
                request.licenseNumber()
        );

        String password = request.password();

        if (password == null || password.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mật khẩu không được để trống"
            );
        }

        // BCrypt thông thường giới hạn 72 byte.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mật khẩu không được vượt quá 72 byte UTF-8"
            );
        }

        // 2. Kiểm tra dữ liệu trùng
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username đã tồn tại"
            );
        }

        if (email != null && userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email đã tồn tại"
            );
        }

        if (driverRepository.existsByDriverCode(driverCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Mã tài xế đã tồn tại"
            );
        }

        if (licenseNumber != null && driverRepository.existsByLicenseNumber(
                licenseNumber
        )){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Số giấy phép lái xe đã tồn tại"
            );
        }

        // 3. Tìm role DRIVER trong database
        RoleEntity driverRole = roleRepository
                .findByName("DRIVER")
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Hệ thống chưa cấu hình role DRIVER"
                        )
                );

        // 4. Tạo tài khoản User.
        UserEntity user = new UserEntity();

        user.setUsername(username);
        user.setPasswordHash(
                passwordEncoder.encode(password)
        );
        user.setFullName(fullName);
        user.setEmail(email);
        user.setActive(true);
        user.setRole(driverRole);

        UserEntity savedUser = userRepository.saveAndFlush(user);

        // 5. Tạo hồ sơ Driver
        DriverEntity driver = new DriverEntity();

        driver.setUser(savedUser);
        driver.setDriverCode(driverCode);
        driver.setPhone(phone);
        driver.setLicenseNumber(licenseNumber);

        DriverEntity savedDriver =
                driverRepository.saveAndFlush(driver);

        // 6. Trả DTO, không trả passwordHash.
        return DriverResponse.fromEntity(savedDriver);
    }

    /**
     * Lấy thông tin tài xế theo Driver ID.
     */
    @Override
    @Transactional(readOnly = true)
    public DriverResponse getDriverById(Long driverId) {

        if (driverId == null || driverId <= 0){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Driver ID phải là số nguyên dương"
            );
        }

        DriverEntity driver = driverRepository
                .findById(driverId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy tài xế với ID: " + driverId
                        )
                );

        return DriverResponse.fromEntity(driver);
    }

    /**
     * Lấy danh sách tài xế.
     */
    @Override
    @Transactional(readOnly = true)
    public List<DriverResponse> getAllDrivers() {
        return driverRepository.findAll()
                .stream()
                .map(DriverResponse::fromEntity)
                .toList();
    }

    /**
     * Chuẩn hóa chuỗi bắt buộc.
     */
    private String requireText(
            String value,
            String fieldName
    ){

        if (value == null || value.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " không được để trống"
            );
        }

        return value.trim();
    }

    /**
     * Chuẩn hóa trường tùy chọn:
     * null hoặc chuỗi trắng -> null.
     */
    private String normalizeOptional(String value){

        if (value == null || value.isBlank()){
            return null;
        }

        return value.trim();
    }

    /**
     * Chuẩn hóa email:
     * - Bỏ khoảng trắng đầu/cuối.
     * - Chuyển về chữ thường.
     */
    private String normalizeEmail(String email){

        String normalized = normalizeOptional(email);

        if (normalized == null){
            return null;
        }

        return normalized.toLowerCase(Locale.ROOT);
    }
}


package com.driveguard.backend.driver.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DriverCreateRequest(

        @NotBlank(message = "Username không được để trống")
        @Size(min = 3, max = 50,
                message = "Username phải có từ 3 đến 50 ký tự")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]+$",
                message = "Username chỉ được chứa chữ cái, số, dấu chấm, gạch dưới và gạch ngang"
        )
        String username,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 72,
                message = "Mật khẩu phải có từ 8 đến 72 ký tự")
        String password,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100,
                message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @Email(message = "Email không đúng định dạng")
        @Size(max = 255,
                message = "Email tối đa 255 ký tự")
        String email,

        @NotBlank(message = "Mã tài xế không được để trống")
        @Size(max = 30,
                message = "Mã tài xế tối đa 30 ký tự")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]+$",
                message = "Mã tài xế chỉ được chứa chữ cái, số, gạch dưới và gạch ngang"
        )
        String driverCode,

        @Pattern(
                regexp = "^$|^[0-9+(). -]{9,20}$",
                message = "Số điện thoại không hợp lệ"
        )
        String phone,

        @Size(max = 50,
                message = "Số giấy phép lái xe tối đa 50 ký tự")
        String licenseNumber

) {
}

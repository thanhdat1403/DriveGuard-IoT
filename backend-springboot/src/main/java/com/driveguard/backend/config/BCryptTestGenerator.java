
package com.driveguard.backend.config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class BCryptTestGenerator {

    public static void main(String[] args) throws Exception {

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        System.in,
                        StandardCharsets.UTF_8
                )
        );

        System.out.print("Nhap mat khau cho viewer_test: ");

        String password = reader.readLine();

        if (password == null || password.isBlank()) {
            System.out.println("Mat khau khong duoc de trong.");
            return;
        }

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder();

        String hash = encoder.encode(password);

        System.out.println("BCrypt hash: " + hash);
    }
}

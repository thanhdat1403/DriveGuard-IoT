package com.driveguard.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendSpringbootApplication {

    public static void main(String[] args) {

        System.out.println(
                "[TIME CHECK] JVM Zone = "
                        + java.time.ZoneId.systemDefault()
        );

        System.out.println(
                "[TIME CHECK] LocalDateTime = "
                        + java.time.LocalDateTime.now()
        );

        System.out.println(
                "[TIME CHECK] Instant UTC = "
                        + java.time.Instant.now()
        );

        SpringApplication.run(BackendSpringbootApplication.class, args);


    }

}

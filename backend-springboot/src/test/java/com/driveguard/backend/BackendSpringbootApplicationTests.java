
package com.driveguard.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BackendSpringbootApplicationTests {

    @Container
    static final MariaDBContainer mariaDB =
            new MariaDBContainer(
                    DockerImageName.parse("mariadb:12.3.2")
            )
                    .withDatabaseName("driveguard_test")
                    .withUsername("driveguard_test")
                    .withPassword("test_password")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource(
                                    "db/schema-test.sql"
                            ),
                            "/docker-entrypoint-initdb.d/01-schema.sql"
                    );

    @DynamicPropertySource
    static void configureDatabase(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                mariaDB::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                mariaDB::getUsername
        );

        registry.add(
                "spring.datasource.password",
                mariaDB::getPassword
        );

        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "validate"
        );

        registry.add(
                "driveguard.mqtt.enabled",
                () -> "false"
        );

        registry.add(
                "app.jwt.secret",
                () -> "driveguard-integration-test-only-secret-key-2026-not-for-production"
        );
    }

    @Test
    void contextLoads() {
    }
}

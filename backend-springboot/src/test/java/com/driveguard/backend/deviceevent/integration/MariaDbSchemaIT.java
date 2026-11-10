
package com.driveguard.backend.deviceevent.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.utility.DockerImageName;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class MariaDbSchemaIT {


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


    @Test
    void shouldInitializeSchemaWithRequiredTablesAndUniqueIndexes()
            throws Exception {

        try (Connection connection = DriverManager.getConnection(
                mariaDB.getJdbcUrl(),
                mariaDB.getUsername(),
                mariaDB.getPassword()
        )) {

            DatabaseMetaData metadata = connection.getMetaData();

            assertTableExists(metadata, "devices");
            assertTableExists(metadata, "device_events");
            assertTableExists(metadata, "alerts");

            assertUniqueIndex(
                    metadata,
                    "device_events",
                    "uk_device_events_event_id"
            );

            assertUniqueIndex(
                    metadata,
                    "alerts",
                    "uk_alerts_device_sos_episode"
            );

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT COUNT(*) FROM devices"
                 )) {

                assertTrue(result.next());
                assertEquals(0, result.getLong(1));
            }
        }
    }

    private void assertTableExists(
            DatabaseMetaData metadata,
            String tableName
    ) throws Exception {

        try (ResultSet tables = metadata.getTables(
                null,
                null,
                tableName,
                new String[]{"TABLE"}
        )) {
            assertTrue(
                    tables.next(),
                    "Missing table: " + tableName
            );
        }
    }

    private void assertUniqueIndex(
            DatabaseMetaData metadata,
            String tableName,
            String indexName
    ) throws Exception {

        Set<String> uniqueIndexes = new HashSet<>();

        try (ResultSet indexes = metadata.getIndexInfo(
                null,
                null,
                tableName,
                true,
                false
        )) {

            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");

                if (name != null) {
                    uniqueIndexes.add(name);
                }
            }
        }

        assertTrue(
                uniqueIndexes.contains(indexName),
                "Missing unique index: " + indexName
        );
    }
}

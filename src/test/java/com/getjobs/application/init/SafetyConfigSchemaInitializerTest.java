package com.getjobs.application.init;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SafetyConfigSchemaInitializer 的幂等补列和默认值测试。
 */
class SafetyConfigSchemaInitializerTest {
    @TempDir
    Path tempDir;

    @Test
    void ensureSafetyColumnsAddsMissingColumnsWithConservativeDefaults() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("config.db").toAbsolutePath());
        createLegacyTables(dataSource);

        SafetyConfigSchemaInitializer initializer = new SafetyConfigSchemaInitializer(dataSource);
        initializer.ensureSafetyColumns();
        initializer.ensureSafetyColumns();

        try (Connection connection = dataSource.getConnection()) {
            assertCommonColumns(connection, "boss_config");
            assertCommonColumns(connection, "liepin_config");
            assertCommonColumns(connection, "job51_config");
            assertCommonColumns(connection, "zhilian_config");
            assertTrue(columnExists(connection, "zhilian_config", "allow_similar_jobs"));
            assertEquals(0, readInt(connection, "zhilian_config", "allow_similar_jobs"));
            assertTrue(columnExists(connection, "boss_config", "browser_profile_mode"));
            assertTrue(columnExists(connection, "boss_config", "min_action_delay_ms"));
            assertTrue(columnExists(connection, "boss_config", "max_action_delay_ms"));
            assertTrue(tableExists(connection, "filter_template"));
            assertEquals("persistent_profile", readString(connection, "boss_config", "browser_profile_mode"));
            assertEquals("cookie_db", readString(connection, "liepin_config", "browser_profile_mode"));
            assertEquals("cookie_db", readString(connection, "job51_config", "browser_profile_mode"));
            assertEquals("cookie_db", readString(connection, "zhilian_config", "browser_profile_mode"));
        }
    }

    private void createLegacyTables(SQLiteDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE boss_config (id INTEGER PRIMARY KEY, debugger INTEGER)");
            statement.execute("CREATE TABLE liepin_config (id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE job51_config (id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE zhilian_config (id INTEGER PRIMARY KEY)");
            statement.execute("INSERT INTO boss_config (debugger) VALUES (1)");
            statement.execute("INSERT INTO liepin_config DEFAULT VALUES");
            statement.execute("INSERT INTO job51_config DEFAULT VALUES");
            statement.execute("INSERT INTO zhilian_config DEFAULT VALUES");
        }
    }

    private void assertCommonColumns(Connection connection, String table) throws Exception {
        assertTrue(columnExists(connection, table, "dry_run"));
        assertTrue(columnExists(connection, table, "max_deliveries"));
        assertTrue(columnExists(connection, table, "stop_on_captcha"));
        assertTrue(columnExists(connection, table, "stop_on_risk_text"));
        assertEquals(1, readInt(connection, table, "dry_run"));
        assertEquals(1, readInt(connection, table, "max_deliveries"));
        assertEquals(1, readInt(connection, table, "stop_on_captcha"));
        assertEquals(1, readInt(connection, table, "stop_on_risk_text"));
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            return resultSet.next();
        }
    }

    private int readInt(Connection connection, String table, String column) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT " + column + " FROM " + table + " LIMIT 1")) {
            assertTrue(resultSet.next());
            return resultSet.getInt(column);
        }
    }

    private String readString(Connection connection, String table, String column) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT " + column + " FROM " + table + " LIMIT 1")) {
            assertTrue(resultSet.next());
            return resultSet.getString(column);
        }
    }
}

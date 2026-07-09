package com.getjobs.application.init;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * 启动时幂等补齐四个平台配置表的安全控制列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SafetyConfigSchemaInitializer {
    private final DataSource dataSource;

    /**
     * 补齐安全配置列，并为旧记录写入保守默认值。
     */
    @PostConstruct
    public void ensureSafetyColumns() {
        try (Connection connection = dataSource.getConnection()) {
            ensureCommonColumns(connection, "boss_config");
            ensureCommonColumns(connection, "liepin_config");
            ensureCommonColumns(connection, "job51_config");
            ensureCommonColumns(connection, "zhilian_config");
            ensureColumn(connection, "zhilian_config", "allow_similar_jobs", "INTEGER DEFAULT 0", "0");
            ensureColumn(connection, "boss_config", "browser_profile_mode", "TEXT DEFAULT 'persistent_profile'", "'persistent_profile'");
            ensureColumn(connection, "boss_config", "min_action_delay_ms", "INTEGER DEFAULT 2500", "2500");
            ensureColumn(connection, "boss_config", "max_action_delay_ms", "INTEGER DEFAULT 6500", "6500");
            ensureColumn(connection, "boss_config", "pause_every_deliveries", "INTEGER DEFAULT 1", "1");
            ensureColumn(connection, "boss_config", "pause_seconds", "INTEGER DEFAULT 20", "20");
            ensureColumn(connection, "liepin_config", "browser_profile_mode", "TEXT DEFAULT 'cookie_db'", "'cookie_db'");
            ensureColumn(connection, "job51_config", "browser_profile_mode", "TEXT DEFAULT 'cookie_db'", "'cookie_db'");
            ensureColumn(connection, "zhilian_config", "browser_profile_mode", "TEXT DEFAULT 'cookie_db'", "'cookie_db'");
            ensureFilterTemplateTable(connection);
            ensureAutomationTables(connection);
        } catch (Exception e) {
            log.warn("补齐投递安全配置列失败: {}", e.getMessage());
        }
    }

    private void ensureCommonColumns(Connection connection, String table) throws Exception {
        if (!tableExists(connection, table)) {
            log.warn("配置表 {} 不存在，跳过安全列补齐", table);
            return;
        }
        List<ColumnSpec> columns = List.of(
                new ColumnSpec("dry_run", "INTEGER DEFAULT 1", "1"),
                new ColumnSpec("max_deliveries", "INTEGER DEFAULT 1", "1"),
                new ColumnSpec("stop_on_captcha", "INTEGER DEFAULT 1", "1"),
                new ColumnSpec("stop_on_risk_text", "INTEGER DEFAULT 1", "1")
        );
        for (ColumnSpec column : columns) {
            ensureColumn(connection, table, column.name(), column.definition(), column.defaultValue());
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void ensureColumn(Connection connection, String table, String column, String definition, String defaultValue) throws Exception {
        if (!tableExists(connection, table)) {
            log.warn("配置表 {} 不存在，跳过列 {}", table, column);
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (!columnExists(connection, table, column)) {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                log.info("已为 {} 补齐安全列 {}", table, column);
            }
            statement.executeUpdate("UPDATE " + table + " SET " + column + " = " + defaultValue + " WHERE " + column + " IS NULL");
        }
    }

    private void ensureFilterTemplateTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS filter_template (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT,
                        keywords TEXT,
                        city TEXT,
                        salary TEXT,
                        degree TEXT,
                        experience TEXT,
                        company_scale TEXT,
                        industry TEXT,
                        job_exclude_words TEXT,
                        company_blacklist TEXT,
                        created_at TEXT,
                        updated_at TEXT
                    )
                    """);
        }
    }

    private void ensureAutomationTables(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS automation_task (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT,
                        platforms TEXT,
                        mode TEXT,
                        status TEXT,
                        filter_template_id INTEGER,
                        keywords TEXT,
                        city TEXT,
                        max_applications INTEGER DEFAULT 1,
                        max_daily_applications INTEGER DEFAULT 1,
                        allow_real_actions INTEGER DEFAULT 0,
                        review_approved INTEGER DEFAULT 0,
                        last_message TEXT,
                        started_at TEXT,
                        completed_at TEXT,
                        created_at TEXT,
                        updated_at TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS automation_audit (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        task_id INTEGER,
                        platform TEXT,
                        event_type TEXT,
                        target_company TEXT,
                        target_job TEXT,
                        target_url TEXT,
                        result TEXT,
                        message TEXT,
                        created_at TEXT
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_automation_task_status ON automation_task(status)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_automation_audit_task_id ON automation_audit(task_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_automation_audit_platform_event_time ON automation_audit(platform, event_type, created_at)");
        }
    }

    private record ColumnSpec(String name, String definition, String defaultValue) {
    }
}

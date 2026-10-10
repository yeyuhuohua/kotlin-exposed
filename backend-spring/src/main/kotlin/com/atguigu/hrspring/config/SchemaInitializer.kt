package com.atguigu.hrspring.config

import com.atguigu.hrspring.auth.PasswordHasher
import com.atguigu.hrspring.auth.PermissionCatalog
import com.atguigu.hrspring.auth.Roles
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

@Configuration
class SchemaInitializer {

    @Bean
    fun schemaInitializerRunner(
        jdbcTemplate: JdbcTemplate,
        passwordHasher: PasswordHasher,
        @Value("\${hr.auth.initialize-schema:false}") initializeSchema: Boolean,
        @Value("\${hr.auth.admin-username:}") adminUsername: String,
        @Value("\${hr.auth.admin-password:}") adminPassword: String,
    ): ApplicationRunner = ApplicationRunner {
        if (!initializeSchema) return@ApplicationRunner
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            error("hr.auth.admin-username and hr.auth.admin-password must be set when schema initialization is enabled")
        }
        CREATE_TABLES.forEach(jdbcTemplate::execute)
        seedRoles(jdbcTemplate)
        seedMenus(jdbcTemplate)
        seedReaderGrants(jdbcTemplate)
        seedAdmin(jdbcTemplate, passwordHasher, adminUsername, adminPassword)
    }

    private fun seedRoles(jdbcTemplate: JdbcTemplate) {
        jdbcTemplate.update(
            "INSERT IGNORE INTO auth_roles (code, name, enabled) VALUES ('ADMIN', 'Administrator', 1), ('READER', 'Read only', 1)",
        )
        jdbcTemplate.update(
            "INSERT IGNORE INTO auth_role_permission_profiles (role_code, revision) VALUES ('ADMIN', 0), ('READER', 0)",
        )
    }

    private fun seedMenus(jdbcTemplate: JdbcTemplate) {
        MENU_SEEDS.forEach { seed ->
            jdbcTemplate.update(
                "INSERT IGNORE INTO auth_menus (`key`, title, path, icon, group_label, sort, admin_only, builtin, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 1)",
                seed.key,
                seed.title,
                seed.path,
                seed.icon,
                seed.groupLabel,
                seed.sort,
                seed.adminOnly,
            )
        }
    }

    private fun seedReaderGrants(jdbcTemplate: JdbcTemplate) {
        val codes = PermissionCatalog.defaults(Roles.READER).toMutableSet()
        MENU_SEEDS.filter { it.adminOnly == 0 }.forEach { codes.add("page:${it.key}") }
        codes.forEach { code ->
            jdbcTemplate.update(
                "INSERT IGNORE INTO auth_role_permissions (role_code, permission_code) VALUES ('READER', ?)",
                code,
            )
        }
    }

    private fun seedAdmin(
        jdbcTemplate: JdbcTemplate,
        passwordHasher: PasswordHasher,
        username: String,
        password: String,
    ) {
        if (username.isBlank() || password.isBlank()) return
        val hash = passwordHasher.hash(password)
        val pending = jdbcTemplate.update(
            "UPDATE auth_users SET password_hash = ? WHERE username = ? AND password_hash = 'PENDING'",
            hash,
            username,
        )
        if (pending > 0) return
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_users WHERE username = ?",
            Int::class.java,
            username,
        ) ?: 0
        if (exists > 0) return
        jdbcTemplate.update(
            "INSERT INTO auth_users (username, password_hash, role_code, enabled, token_version) VALUES (?, ?, 'ADMIN', 1, 0)",
            username,
            hash,
        )
    }
}

private data class MenuSeed(
    val key: String,
    val title: String,
    val path: String,
    val icon: String?,
    val groupLabel: String,
    val sort: Int,
    val adminOnly: Int,
)

private val MENU_SEEDS = listOf(
    MenuSeed("overview", "工作概览", "/", "LayoutDashboard", "工作空间", 10, 0),
    MenuSeed("chat", "智能助手", "/chat", "Bot", "工作空间", 15, 1),
    MenuSeed("employees", "员工管理", "/employees", "Users", "工作空间", 20, 0),
    MenuSeed("departments", "部门管理", "/departments", "Building2", "工作空间", 30, 0),
    MenuSeed("jobs", "岗位管理", "/jobs", "BriefcaseBusiness", "工作空间", 40, 0),
    MenuSeed("locations", "办公地点", "/locations", "MapPin", "工作空间", 50, 0),
    MenuSeed("job-history", "任职历史", "/job-history", "FolderClock", "组织资料", 60, 0),
    MenuSeed("job-grades", "薪资等级", "/job-grades", "BookOpen", "组织资料", 70, 0),
    MenuSeed("countries", "国家与地区", "/countries", "Globe2", "组织资料", 80, 0),
    MenuSeed("regions", "区域目录", "/regions", "Globe2", "组织资料", 90, 0),
    MenuSeed("emp-details", "员工详情视图", "/emp-details", "ContactRound", "组织资料", 100, 0),
    MenuSeed("users", "用户管理", "/users", "ShieldCheck", "访问控制", 110, 1),
    MenuSeed("roles", "角色权限", "/roles", "ShieldCheck", "访问控制", 120, 1),
    MenuSeed("audit", "审计日志", "/audit", "ScrollText", "访问控制", 130, 1),
    MenuSeed("menus", "菜单管理", "/menus", "SquareMenu", "访问控制", 140, 1),
    MenuSeed("t-dept", "示例部门", "/t-dept", null, "演示数据", 150, 0),
    MenuSeed("t-emp", "示例人员", "/t-emp", null, "演示数据", 160, 0),
    MenuSeed("orders", "示例订单", "/orders", null, "演示数据", 170, 0),
    MenuSeed("system", "系统状态", "/system", "Activity", "", 999, 0),
)

private val CREATE_TABLES = listOf(
    """
    CREATE TABLE IF NOT EXISTS auth_roles (
        code varchar(20) NOT NULL,
        name varchar(50) NOT NULL,
        enabled tinyint(1) NOT NULL DEFAULT 1,
        PRIMARY KEY (code)
    ) DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS auth_users (
        id int NOT NULL AUTO_INCREMENT,
        username varchar(64) NOT NULL,
        password_hash varchar(255) NOT NULL,
        role_code varchar(20) NOT NULL,
        enabled tinyint(1) NOT NULL DEFAULT 1,
        token_version int NOT NULL DEFAULT 0,
        PRIMARY KEY (id),
        UNIQUE KEY uk_username (username)
    ) DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS auth_role_permission_profiles (
        role_code varchar(20) NOT NULL,
        revision int NOT NULL DEFAULT 0,
        PRIMARY KEY (role_code)
    ) DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS auth_role_permissions (
        role_code varchar(20) NOT NULL,
        permission_code varchar(160) NOT NULL,
        PRIMARY KEY (role_code, permission_code)
    ) DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS auth_menus (
        `key` varchar(40) NOT NULL,
        title varchar(30) NOT NULL,
        path varchar(100) NOT NULL,
        icon varchar(40) NULL,
        group_label varchar(20) NOT NULL,
        sort int NOT NULL,
        admin_only tinyint(1) NOT NULL DEFAULT 0,
        builtin tinyint(1) NOT NULL DEFAULT 0,
        enabled tinyint(1) NOT NULL DEFAULT 1,
        PRIMARY KEY (`key`),
        UNIQUE KEY uk_path (path)
    ) DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS audit_login_records (
        id bigint NOT NULL AUTO_INCREMENT,
        username varchar(64) NOT NULL,
        user_id int NULL,
        ip varchar(45) NOT NULL,
        user_agent varchar(255) NULL,
        success tinyint(1) NOT NULL,
        error_code varchar(40) NULL,
        created_at datetime NOT NULL,
        PRIMARY KEY (id),
        KEY idx_created_at (created_at),
        KEY idx_username (username)
    ) DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS audit_api_calls (
        id bigint NOT NULL AUTO_INCREMENT,
        user_id int NULL,
        username varchar(64) NULL,
        method varchar(8) NOT NULL,
        path varchar(255) NOT NULL,
        query_string varchar(255) NULL,
        status_code int NOT NULL,
        duration_ms bigint NOT NULL,
        ip varchar(45) NOT NULL,
        user_agent varchar(255) NULL,
        created_at datetime NOT NULL,
        PRIMARY KEY (id),
        KEY idx_created_at (created_at),
        KEY idx_method_path (method, path)
    ) DEFAULT CHARSET=utf8mb4
    """,
)

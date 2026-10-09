package com.atguigu.hr.auth

import kotlinx.serialization.Serializable

/** 登记接口权限编码；未登记的受保护接口默认拒绝访问。页面清单已移到菜单表（menu 包），不再硬编码。 */

@Serializable
data class PermissionDefinition(
    val code: String,
    val kind: String,
    val label: String,
    val group: String,
    val path: String,
    val method: String? = null,
    val adminOnly: Boolean = false,
)

object PermissionCatalog {
    private fun api(method: String, path: String, label: String, group: String, adminOnly: Boolean = false) =
        PermissionDefinition("api:$method:/api$path", "API", label, group, "/api$path", method, adminOnly)

    val definitions: List<PermissionDefinition> = listOf(
        api("GET", "/overview", "查询概览（包含跨模块统计和员工样本）", "工作概览"),
        api("GET", "/employees", "分页查询员工", "员工管理"),
        api("POST", "/employees", "新增员工", "员工管理"),
        api("GET", "/employees/{id}", "查询单个员工", "员工管理"),
        api("PUT", "/employees/{id}", "修改员工", "员工管理"),
        api("PATCH", "/employees/{id}", "精确修改员工（可清空可空字段）", "员工管理"),
        api("DELETE", "/employees/{id}", "删除员工", "员工管理"),
        api("GET", "/employees/{id}/details", "查询员工关联详情", "员工管理"),
        api("GET", "/emp-details", "分页查询员工详情视图", "员工详情视图"),
        api("GET", "/departments", "查询部门列表", "部门管理"),
        api("POST", "/departments", "新增部门", "部门管理"),
        api("GET", "/departments/{id}", "查询单个部门", "部门管理"),
        api("PUT", "/departments/{id}", "修改部门", "部门管理"),
        api("GET", "/departments/{id}/employees", "查询部门员工", "部门管理"),
        api("GET", "/jobs", "查询岗位列表", "岗位管理"),
        api("POST", "/jobs", "新增岗位", "岗位管理"),
        api("PUT", "/jobs/{id}", "修改岗位", "岗位管理"),
        api("GET", "/locations", "查询地点列表", "办公地点"),
        api("POST", "/locations", "新增地点", "办公地点"),
        api("PUT", "/locations/{id}", "修改地点", "办公地点"),
        api("GET", "/countries", "查询国家", "国家与地区"),
        api("GET", "/regions", "查询区域", "区域目录"),
        api("GET", "/job-history", "查询任职历史", "任职历史"),
        api("GET", "/job-grades", "查询薪资等级", "薪资等级"),
        api("GET", "/t-dept", "查询示例部门", "示例部门"),
        api("POST", "/t-dept", "新增示例部门", "示例部门"),
        api("PUT", "/t-dept/{id}", "修改示例部门", "示例部门"),
        api("GET", "/t-emp", "查询示例人员", "示例人员"),
        api("POST", "/t-emp", "新增示例人员", "示例人员"),
        api("PUT", "/t-emp/{id}", "修改示例人员", "示例人员"),
        api("GET", "/orders", "查询示例订单", "示例订单"),
        api("GET", "/auth/users", "查询用户", "用户管理", true),
        api("POST", "/auth/users", "创建用户", "用户管理", true),
        api("PUT", "/auth/users/{id}", "修改用户、角色和密码", "用户管理", true),
        api("DELETE", "/auth/users/{id}", "删除用户", "用户管理", true),
        api("GET", "/auth/roles", "查询角色", "角色权限", true),
        api("POST", "/auth/roles", "创建角色", "角色权限", true),
        api("PUT", "/auth/roles/{code}", "修改角色名称和状态", "角色权限", true),
        api("DELETE", "/auth/roles/{code}", "删除角色", "角色权限", true),
        api("GET", "/auth/permissions", "查询权限目录", "权限管理", true),
        api("GET", "/auth/roles/{code}/permissions", "查看角色权限", "权限管理", true),
        api("PUT", "/auth/roles/{code}/permissions", "修改角色权限", "权限管理", true),
        api("GET", "/audit/logins", "查询登录记录", "审计日志", true),
        api("GET", "/audit/api-calls", "查询接口调用记录", "审计日志", true),
        api("GET", "/menus", "查询菜单列表", "菜单管理", true),
        api("POST", "/menus", "新增菜单", "菜单管理", true),
        api("PUT", "/menus/{key}", "修改菜单", "菜单管理", true),
        api("DELETE", "/menus/{key}", "删除菜单", "菜单管理", true),
    )

    val byCode = definitions.associateBy { it.code }
    private val apiPatterns = definitions.map { definition ->
        definition to apiPathPattern(definition.path)
    }

    fun requiredApi(method: String, path: String): PermissionDefinition? {
        val normalizedMethod = if (method == "HEAD") "GET" else method
        return apiPatterns.firstOrNull { (definition, pattern) ->
            definition.method == normalizedMethod && pattern.matches(path)
        }?.first
    }

    fun defaults(roleCode: String): Set<String> = definitions.filter {
        roleCode == RoleCode.ADMIN.name || (roleCode == RoleCode.READER.name && !it.adminOnly && it.method == "GET")
    }.mapTo(linkedSetOf()) { it.code }

    /**
     * 用户的有效权限。接口码必须在目录里登记且遵守 ADMIN 限制；
     * page: 前缀的页面码来自菜单表（运行时数据），这里原样放行，
     * 是否存在、是否仅管理员可见由菜单读取方（MenuService）判定。
     */
    fun effective(user: AuthUser): Set<String> {
        val configured = if (user.roleCode == RoleCode.ADMIN.name) defaults(user.roleCode) else user.rolePermissions
        return configured.filterTo(linkedSetOf()) { code ->
            if (code.startsWith("page:")) return@filterTo true
            val definition = byCode[code]
            definition != null && (!definition.adminOnly || user.roleCode == RoleCode.ADMIN.name)
        }
    }
}

/** 路径参数段（任意名字，如 {id}、{code}、{employeeId}）匹配单层非斜杠路径。 */
private val pathParameter = Regex("\\{[^/{}]+}")

/**
 * 把登记的路径模板编译成正则。只认 `{...}` 形式，其余字符按字面量转义，
 * 避免新增路由时因为参数名不认识而退化成字面量、导致权限永远匹配不上。
 */
internal fun apiPathPattern(path: String): Regex =
    Regex(path.split('/').joinToString("/") { segment ->
        if (pathParameter.matches(segment)) "[^/]+" else Regex.escape(segment)
    })

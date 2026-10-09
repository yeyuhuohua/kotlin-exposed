package com.atguigu.hr.menu

import com.atguigu.hr.auth.RolePermissions
import com.atguigu.hr.config.DatabaseFactory.dbQuery
import com.atguigu.hr.config.DatabaseFactory.dbUpdate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertIgnore
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

/** 菜单表的读写与启动播种；删除菜单时同事务清掉各角色持有的页面授权，避免留下悬空授权码。 */
object MenuRepository {

    /** 幂等建表并补齐缺失的内置菜单；已存在的行不覆盖，保留管理员在界面上的修改。 */
    suspend fun initialize() = dbUpdate {
        SchemaUtils.create(Menus)
        builtinSeeds.forEach { seed ->
            Menus.insertIgnore {
                it[key] = seed.key
                it[title] = seed.title
                it[path] = seed.path
                it[icon] = seed.icon
                it[groupLabel] = seed.group
                it[sort] = seed.sort
                it[adminOnly] = seed.adminOnly
                it[builtin] = true
                it[enabled] = true
            }
        }
    }

    suspend fun list(): List<MenuDto> = dbQuery {
        Menus.selectAll()
            .orderBy(Menus.sort to SortOrder.ASC, Menus.key to SortOrder.ASC)
            .map { it.toMenuDto() }
            .toList()
    }

    suspend fun find(key: String): MenuDto? = dbQuery {
        Menus.selectAll().where { Menus.key eq key }.firstOrNull()?.toMenuDto()
    }

    suspend fun create(request: MenuCreateRequest): MenuDto = dbUpdate {
        Menus.insert {
            it[key] = request.key
            it[title] = request.title.trim()
            it[path] = request.path
            it[icon] = request.icon?.takeIf(String::isNotBlank)
            it[groupLabel] = request.group.trim()
            it[sort] = request.sort
            it[adminOnly] = request.adminOnly
            it[builtin] = false
            it[enabled] = request.enabled
        }
        MenuDto(
            request.key, request.title.trim(), request.path, request.icon?.takeIf(String::isNotBlank),
            request.group.trim(), request.sort, request.adminOnly, builtin = false, enabled = request.enabled,
        )
    }

    /** 整体替换可编辑字段；adminOnly 与 builtin 不在请求里，保持原值。 */
    suspend fun update(key: String, request: MenuUpdateRequest): MenuDto? = dbUpdate {
        val rows = Menus.update({ Menus.key eq key }) {
            it[title] = request.title.trim()
            it[path] = request.path
            it[icon] = request.icon?.takeIf(String::isNotBlank)
            it[groupLabel] = request.group.trim()
            it[sort] = request.sort
            it[enabled] = request.enabled
        }
        if (rows == 0) return@dbUpdate null
        Menus.selectAll().where { Menus.key eq key }.firstOrNull()?.toMenuDto()
    }

    /** 删除菜单，并清理所有角色持有的 page:<key> 授权（同事务，不留悬空授权码）。 */
    suspend fun delete(key: String): Boolean = dbUpdate {
        RolePermissions.deleteWhere { permissionCode eq "page:$key" }
        Menus.deleteWhere { Menus.key eq key } > 0
    }
}

private fun ResultRow.toMenuDto() = MenuDto(
    key = this[Menus.key],
    title = this[Menus.title],
    path = this[Menus.path],
    icon = this[Menus.icon],
    group = this[Menus.groupLabel],
    sort = this[Menus.sort],
    adminOnly = this[Menus.adminOnly],
    builtin = this[Menus.builtin],
    enabled = this[Menus.enabled],
)

private data class MenuSeed(
    val key: String,
    val title: String,
    val path: String,
    val icon: String?,
    val group: String,
    val sort: Int,
    val adminOnly: Boolean = false,
)

/** 内置菜单：与前端视图组件映射一一对应；新增页面在这里补一行，并在前端补组件。 */
private val builtinSeeds = listOf(
    MenuSeed("overview", "工作概览", "/", "LayoutDashboard", "工作空间", 10),
    MenuSeed("employees", "员工管理", "/employees", "Users", "工作空间", 20),
    MenuSeed("departments", "部门管理", "/departments", "Building2", "工作空间", 30),
    MenuSeed("jobs", "岗位管理", "/jobs", "BriefcaseBusiness", "工作空间", 40),
    MenuSeed("locations", "办公地点", "/locations", "MapPin", "工作空间", 50),
    MenuSeed("job-history", "任职历史", "/job-history", "FolderClock", "组织资料", 60),
    MenuSeed("job-grades", "薪资等级", "/job-grades", "BookOpen", "组织资料", 70),
    MenuSeed("countries", "国家与地区", "/countries", "Globe2", "组织资料", 80),
    MenuSeed("regions", "区域目录", "/regions", "Globe2", "组织资料", 90),
    MenuSeed("emp-details", "员工详情视图", "/emp-details", "ContactRound", "组织资料", 100),
    MenuSeed("users", "用户管理", "/users", "ShieldCheck", "访问控制", 110, adminOnly = true),
    MenuSeed("roles", "角色权限", "/roles", "ShieldCheck", "访问控制", 120, adminOnly = true),
    MenuSeed("audit", "审计日志", "/audit", "ScrollText", "访问控制", 130, adminOnly = true),
    MenuSeed("menus", "菜单管理", "/menus", "SquareMenu", "访问控制", 140, adminOnly = true),
    MenuSeed("t-dept", "示例部门", "/t-dept", null, "演示数据", 150),
    MenuSeed("t-emp", "示例人员", "/t-emp", null, "演示数据", 160),
    MenuSeed("orders", "示例订单", "/orders", null, "演示数据", 170),
    MenuSeed("system", "系统状态", "/system", "Activity", "", 999),
)

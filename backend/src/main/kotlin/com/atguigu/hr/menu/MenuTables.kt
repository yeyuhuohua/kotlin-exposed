package com.atguigu.hr.menu

import org.jetbrains.exposed.v1.core.Table

/** 菜单（页面）表的 Exposed 映射：页面清单的运行时来源，替代原先硬编码在权限目录里的 page 定义。 */
object Menus : Table("auth_menus") {
    val key = varchar("key", 40)
    val title = varchar("title", 30)
    val path = varchar("path", 100).uniqueIndex()
    /** 前端图标注册表里的名字；为空时前端用默认图标。 */
    val icon = varchar("icon", 40).nullable()
    /** 侧栏分组名；空串表示固定在侧栏底部（如系统状态）。 */
    val groupLabel = varchar("group_label", 20)
    val sort = integer("sort")
    /** 仅 ADMIN 可见可授权；创建后不可再改，避免角色持有的授权腐烂。 */
    val adminOnly = bool("admin_only").default(false)
    /** 内置菜单随启动播种，不允许删除。 */
    val builtin = bool("builtin").default(false)
    val enabled = bool("enabled").default(true)
    override val primaryKey = PrimaryKey(key)
}

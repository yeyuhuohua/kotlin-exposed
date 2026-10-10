package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.MenuCreateRequest
import com.atguigu.hrspring.dto.MenuDto
import com.atguigu.hrspring.dto.MenuUpdateRequest
import com.atguigu.hrspring.service.MenuService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class MenuMcpTools(
    private val menuService: MenuService,
) {
    @McpTool(name = "list_menus", description = "查询全部菜单，包含已停用的菜单。")
    fun listMenus(): List<MenuDto> = menuService.list().first

    @McpTool(name = "create_menu", description = "新增菜单。key 与路径全局唯一，adminOnly 创建后不可再改。")
    fun createMenu(
        @McpToolParam(description = "菜单 key，2-40 位，小写字母开头，可含数字和连字符", required = true) key: String,
        @McpToolParam(description = "标题，1-30 个字符", required = true) title: String,
        @McpToolParam(description = "路径，以 / 开头，只能含小写字母、数字、- _ /", required = true) path: String,
        @McpToolParam(description = "图标名，字母开头，可空", required = false) icon: String?,
        @McpToolParam(description = "侧栏分组，默认工作空间；空串表示固定在底部", required = false) group: String?,
        @McpToolParam(description = "排序，0 到 9999，默认 100", required = false) sort: Int?,
        @McpToolParam(description = "是否仅 ADMIN 可见，创建后不可改，默认 false", required = false) adminOnly: Boolean?,
        @McpToolParam(description = "是否启用，默认 true", required = false) enabled: Boolean?,
    ): MenuDto = menuService.create(
        MenuCreateRequest(
            key = key,
            title = title,
            path = path,
            icon = icon,
            group = group ?: "工作空间",
            sort = sort ?: 100,
            adminOnly = adminOnly ?: false,
            enabled = enabled ?: true,
        ),
    )

    @McpTool(name = "update_menu", description = "整体替换菜单的标题、路径、图标、分组、排序和启用状态。不改 key、adminOnly 和 builtin。")
    fun updateMenu(
        @McpToolParam(description = "菜单 key", required = true) key: String,
        @McpToolParam(description = "标题，1-30 个字符", required = true) title: String,
        @McpToolParam(description = "路径，以 / 开头", required = true) path: String,
        @McpToolParam(description = "图标名，不传则清空", required = false) icon: String?,
        @McpToolParam(description = "侧栏分组，不传则回到工作空间；空串表示固定在底部", required = false) group: String?,
        @McpToolParam(description = "排序，0 到 9999，不传则 100", required = false) sort: Int?,
        @McpToolParam(description = "是否启用，不传则 true。菜单管理、用户管理、角色权限不可停用", required = false) enabled: Boolean?,
    ): MenuDto = menuService.update(
        key,
        MenuUpdateRequest(
            title = title,
            path = path,
            icon = icon,
            group = group ?: "工作空间",
            sort = sort ?: 100,
            enabled = enabled ?: true,
        ),
    )

    @McpTool(name = "delete_menu", description = "删除菜单，并清理各角色持有的 page:key 授权。内置菜单不可删除。")
    fun deleteMenu(
        @McpToolParam(description = "菜单 key", required = true) key: String,
    ): String {
        menuService.delete(key)
        return "deleted"
    }
}

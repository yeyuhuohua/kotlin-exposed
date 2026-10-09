package com.atguigu.hr.menu

import com.atguigu.hr.auth.AuthUser
import com.atguigu.hr.auth.PermissionDefinition
import com.atguigu.hr.auth.RoleCode
import java.util.concurrent.atomic.AtomicReference

/** 菜单读取入口：短 TTL 缓存吸收每次导航的重复查询，写操作后立即失效。 */
object MenuService {
    private const val CACHE_TTL_MILLIS = 3_000L

    private data class CachedMenus(val expiresAtMillis: Long, val menus: List<MenuDto>)

    private val cache = AtomicReference<CachedMenus?>()

    /** 全量菜单（含停用），按 sort、key 排序；管理界面与授权校验用。 */
    suspend fun catalog(): List<MenuDto> {
        val now = System.currentTimeMillis()
        cache.get()?.takeIf { it.expiresAtMillis > now }?.let { return it.menus }
        val fresh = MenuRepository.list()
        cache.set(CachedMenus(now + CACHE_TTL_MILLIS, fresh))
        return fresh
    }

    /** 启用菜单的 key 集合；装饰当前用户的页面权限码时用。 */
    suspend fun enabledKeys(): Set<String> = catalog().filter { it.enabled }.mapTo(linkedSetOf()) { it.key }

    /** 当前用户可访问的页面清单：ADMIN 看全部启用菜单，其他角色按授权交集且排除仅 ADMIN 菜单。 */
    suspend fun pagesFor(user: AuthUser): List<PageRoute> = pagesFor(user, catalog())

    /** 角色权限目录的页面部分：与菜单表实时一致，新增菜单立即可授权。 */
    suspend fun pageDefinitions(): List<PermissionDefinition> = catalog().map { menu ->
        PermissionDefinition(
            code = "page:${menu.key}",
            kind = "PAGE",
            label = menu.title,
            group = menu.group.ifEmpty { "侧栏固定" },
            path = menu.path,
            adminOnly = menu.adminOnly,
        )
    }

    suspend fun create(request: MenuCreateRequest): MenuDto = MenuRepository.create(request).also { invalidate() }

    suspend fun update(key: String, request: MenuUpdateRequest): MenuDto? =
        MenuRepository.update(key, request)?.also { invalidate() }

    suspend fun delete(key: String): Boolean = MenuRepository.delete(key).also { if (it) invalidate() }

    fun invalidate() {
        cache.set(null)
    }

    internal fun pagesFor(user: AuthUser, menus: List<MenuDto>): List<PageRoute> {
        val enabled = menus.filter { it.enabled }
        val visible = if (user.roleCode == RoleCode.ADMIN.name) {
            enabled
        } else {
            enabled.filter { !it.adminOnly && "page:${it.key}" in user.rolePermissions }
        }
        return visible.map { menu ->
            PageRoute(menu.key, menu.title, menu.path, menu.icon, menu.group, menu.sort, menu.adminOnly)
        }
    }
}

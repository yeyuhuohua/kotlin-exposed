package com.atguigu.hrspring.service
import com.atguigu.hrspring.auth.AuthUser
import com.atguigu.hrspring.auth.Roles
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.common.cache.RedisCache
import com.atguigu.hrspring.dto.MenuCreateRequest
import com.atguigu.hrspring.dto.MenuDto
import com.atguigu.hrspring.dto.MenuPermissionDto
import com.atguigu.hrspring.dto.MenuUpdateRequest
import com.atguigu.hrspring.dto.PageRoute
import com.atguigu.hrspring.dto.menuKeyError
import com.atguigu.hrspring.entity.MenuEntity
import com.atguigu.hrspring.mapper.MenuMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 菜单读取走 Redis（[CacheNames.MENUS]）。
 * 删除时用 JDBC 清掉 page:key 授权，不注入 auth 模块的 Mapper。
 */
@Service
class MenuService(
    private val menuMapper: MenuMapper,
    private val redisCache: RedisCache,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(): Pair<List<MenuDto>, Boolean> =
        redisCache.withCacheList(CacheNames.MENUS, MenuDto::class.java) {
            menuMapper.selectList(
                QueryWrapper<MenuEntity>().orderByAsc("sort", "`key`"),
            ).map { it.toDto() }
        }

    /** 启用菜单的 key，给当前用户装饰页面权限码。 */
    @Transactional(readOnly = true)
    fun enabledKeys(): Set<String> =
        list().first.filter { it.enabled }.mapTo(linkedSetOf()) { it.key }

    /** ADMIN 看全部启用菜单；其他角色按授权交集，并排除仅 ADMIN 菜单。 */
    @Transactional(readOnly = true)
    fun pagesFor(user: AuthUser): List<PageRoute> {
        val enabled = list().first.filter { it.enabled }
        val visible = if (user.roleCode == Roles.ADMIN) {
            enabled
        } else {
            enabled.filter { menu -> !menu.adminOnly && "page:${menu.key}" in user.rolePermissions }
        }
        return visible.map { menu ->
            PageRoute(
                key = menu.key,
                title = menu.title,
                path = menu.path,
                icon = menu.icon,
                group = menu.group,
                sort = menu.sort,
                adminOnly = menu.adminOnly,
            )
        }
    }

    /** 角色权限目录的页面部分。code 形如 page:{key}；空分组显示为「侧栏固定」。 */
    @Transactional(readOnly = true)
    fun pageDefinitions(): List<MenuPermissionDto> = list().first.map { menu ->
        MenuPermissionDto(
            code = "page:${menu.key}",
            kind = "PAGE",
            label = menu.title,
            group = menu.group.ifEmpty { "侧栏固定" },
            path = menu.path,
            method = null,
            adminOnly = menu.adminOnly,
        )
    }

    @Transactional
    fun create(request: MenuCreateRequest): MenuDto {
        request.contentError()?.let { throw ApiException.badRequest(it) }
        val entity = MenuEntity().apply {
            key = request.key
            title = request.title.trim()
            path = request.path
            icon = request.icon?.takeIf { it.isNotBlank() }
            groupLabel = request.group.trim()
            sort = request.sort
            adminOnly = request.adminOnly
            builtin = false
            enabled = request.enabled
        }
        try {
            menuMapper.insert(entity)
        } catch (e: DataIntegrityViolationException) {
            log.warn("menu create conflict: {}", e.message)
            throw ApiException.conflict("resource conflict")
        }
        evict()
        return entity.toDto()
    }

    @Transactional
    fun update(key: String, request: MenuUpdateRequest): MenuDto {
        if (menuKeyError(key) != null) throw ApiException.badRequest("invalid menu key")
        request.contentError()?.let { throw ApiException.badRequest(it) }
        if (key in PROTECTED_MENU_KEYS && !request.enabled) {
            throw ApiException.badRequest("management entry cannot be disabled")
        }
        if (lock(key) == null) throw ApiException.notFound("menu not found")
        val wrapper = UpdateWrapper<MenuEntity>()
            .eq("`key`", key)
            .set("title", request.title.trim())
            .set("path", request.path)
            .set("group_label", request.group.trim())
            .set("sort", request.sort)
            .set("enabled", request.enabled)
        val icon = request.icon?.takeIf { it.isNotBlank() }
        if (icon == null) {
            wrapper.setSql("icon = NULL")
        } else {
            wrapper.set("icon", icon)
        }
        try {
            menuMapper.update(wrapper)
        } catch (e: DataIntegrityViolationException) {
            log.warn("menu update conflict: {}", e.message)
            throw ApiException.conflict("resource conflict")
        }
        val saved = menuMapper.selectById(key)?.toDto() ?: error("menu update missing row")
        evict()
        return saved
    }

    @Transactional
    fun delete(key: String) {
        if (menuKeyError(key) != null) throw ApiException.badRequest("invalid menu key")
        val existing = lock(key) ?: throw ApiException.notFound("menu not found")
        if (existing.builtin) throw ApiException.forbidden("builtin menu is protected")
        jdbcTemplate.update(
            "DELETE FROM auth_role_permissions WHERE permission_code = ?",
            "page:$key",
        )
        menuMapper.delete(QueryWrapper<MenuEntity>().eq("`key`", key))
        evict()
    }

    /** selectList 走当前事务。selectOne 会另开 SqlSession，FOR UPDATE 锁不住后面的更新。 */
    private fun lock(key: String): MenuEntity? =
        menuMapper.selectList(
            QueryWrapper<MenuEntity>().eq("`key`", key).last("FOR UPDATE"),
        ).firstOrNull()

    private fun evict() {
        redisCache.invalidateAfterCommit(listOf(CacheNames.MENUS))
    }

    private fun MenuEntity.toDto() = MenuDto(
        key = key,
        title = title,
        path = path,
        icon = icon,
        group = groupLabel,
        sort = sort,
        adminOnly = adminOnly,
        builtin = builtin,
        enabled = enabled,
    )

    private companion object {
        /** 停用后管理员会失去对应管理界面。 */
        val PROTECTED_MENU_KEYS = setOf("menus", "users", "roles")
    }
}

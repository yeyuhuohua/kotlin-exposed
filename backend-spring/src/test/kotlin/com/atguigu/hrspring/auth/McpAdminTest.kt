package com.atguigu.hrspring.auth

import com.atguigu.hrspring.audit.AuditService
import com.atguigu.hrspring.common.api.ClientIp
import com.atguigu.hrspring.config.HrProperties
import com.atguigu.hrspring.entity.RoleEntity
import com.atguigu.hrspring.entity.UserEntity
import com.atguigu.hrspring.mapper.RoleMapper
import com.atguigu.hrspring.mapper.RolePermissionMapper
import com.atguigu.hrspring.mapper.RolePermissionProfileMapper
import com.atguigu.hrspring.mapper.UserMapper
import com.atguigu.hrspring.service.AuthService
import com.atguigu.hrspring.service.MenuService
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.any
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpAdminTest {
    @Test
    fun `服务口令使用配置的管理员并拒绝停用或降权账号`() {
        val properties = HrProperties(auth = HrProperties.AuthProperties(adminUsername = "operator"))
        val users = mock(UserMapper::class.java)
        val roles = mock(RoleMapper::class.java)
        val permissions = mock(RolePermissionMapper::class.java)
        `when`(permissions.selectList(any())).thenReturn(emptyList())
        val row = UserEntity().apply { id = 7; username = "operator"; roleCode = "ADMIN" }
        val role = RoleEntity().apply { code = "ADMIN" }
        `when`(users.selectOne(any())).thenAnswer { invocation ->
            val query = invocation.getArgument<QueryWrapper<UserEntity>>(0)
            // 强制触发参数绑定，与真实 Mapper 的 SQL 构建顺序一致。
            query.sqlSegment
            assertTrueOperator(query.paramNameValuePairs.values)
            row
        }
        `when`(roles.selectById("ADMIN")).thenReturn(role)
        val tx = mock(PlatformTransactionManager::class.java)
        `when`(tx.getTransaction(any())).thenReturn(SimpleTransactionStatus())
        val service = AuthService(
            tx, users, roles, permissions, mock(RolePermissionProfileMapper::class.java),
            mock(PasswordHasher::class.java), mock(TokenService::class.java), AuthUserCache(properties),
            LoginThrottle(properties), mock(MenuService::class.java), mock(AuditService::class.java),
            ClientIp(properties), properties,
        )
        assertEquals("operator", service.loadActiveAdmin()?.username)
        row.enabled = false
        assertNull(service.loadActiveAdmin())
        row.enabled = true
        role.enabled = false
        assertNull(service.loadActiveAdmin())
        role.enabled = true
        row.roleCode = "READER"
        `when`(roles.selectById("READER")).thenReturn(RoleEntity().apply { code = "READER" })
        assertNull(service.loadActiveAdmin())
    }

    private fun assertTrueOperator(values: Collection<Any>) {
        assertEquals(listOf("operator"), values.toList())
    }
}

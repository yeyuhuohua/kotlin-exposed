package com.atguigu.hrspring.auth

import com.atguigu.hrspring.audit.AuditService
import com.atguigu.hrspring.common.api.ApiException
import com.baomidou.mybatisplus.core.MybatisConfiguration
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper
import org.apache.ibatis.builder.MapperBuilderAssistant
import com.atguigu.hrspring.common.api.ClientIp
import com.atguigu.hrspring.common.api.ErrorCode
import com.atguigu.hrspring.config.HrProperties
import com.atguigu.hrspring.dto.UserCreateRequest
import com.atguigu.hrspring.entity.UserEntity
import com.atguigu.hrspring.mapper.RoleMapper
import com.atguigu.hrspring.mapper.RolePermissionMapper
import com.atguigu.hrspring.mapper.RolePermissionProfileMapper
import com.atguigu.hrspring.mapper.UserMapper
import com.atguigu.hrspring.service.AuthService
import com.atguigu.hrspring.service.MenuService
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.transaction.PlatformTransactionManager
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthAccountProtectionTest {
    @kotlin.test.BeforeTest
    fun initTableInfo() {
        TableInfoHelper.initTableInfo(MapperBuilderAssistant(MybatisConfiguration(), ""), UserEntity::class.java)
    }

    @Test
    fun `普通新增接口保留默认及配置的管理员用户名`() {
        val properties = HrProperties(auth = HrProperties.AuthProperties(adminUsername = " Operator "))
        val users = mock(UserMapper::class.java)
        val roles = mock(RoleMapper::class.java)
        val transactions = mock(PlatformTransactionManager::class.java)
        val service = AuthService(
            transactions, users, roles, mock(RolePermissionMapper::class.java),
            mock(RolePermissionProfileMapper::class.java), mock(PasswordHasher::class.java),
            mock(TokenService::class.java), AuthUserCache(properties), LoginThrottle(properties),
            mock(MenuService::class.java), mock(AuditService::class.java), ClientIp(properties), properties,
        )
        for (username in listOf("admin", " AdMiN ", "operator", " OPERATOR ")) {
            val error = assertFailsWith<ApiException> {
                service.createUser(UserCreateRequest(username, "password123"))
            }
            assertEquals(409, error.status)
            assertEquals(ErrorCode.USERNAME_TAKEN, error.error)
        }
        verifyNoInteractions(users, roles, transactions)
    }

    @Test
    fun `用户列表及JSON包含默认与配置管理员的保护标记`() {
        val properties = HrProperties(auth = HrProperties.AuthProperties(adminUsername = "operator"))
        val users = mock(UserMapper::class.java)
        val admin = UserEntity().apply { id = 1; username = "admin"; roleCode = "ADMIN" }
        val operator = UserEntity().apply { id = 2; username = "operator"; roleCode = "ADMIN" }
        val reader = UserEntity().apply { id = 3; username = "reader"; roleCode = "READER" }
        `when`(users.selectCount(org.mockito.Mockito.any())).thenReturn(3L)
        `when`(users.selectList(org.mockito.Mockito.any())).thenReturn(listOf(admin, operator, reader))
        val service = authService(properties, users, mock(RoleMapper::class.java), mock(PlatformTransactionManager::class.java))

        val result = service.listUsers(50, 0)

        assertEquals(
            mapOf("admin" to true, "operator" to true, "reader" to false),
            result.items.associate { it.username to it.protected },
        )
        assertTrue("\"protected\":true" in jacksonObjectMapper().writeValueAsString(result.items))
    }

    private fun authService(
        properties: HrProperties,
        users: UserMapper,
        roles: RoleMapper,
        transactions: PlatformTransactionManager,
    ) = AuthService(
        transactions, users, roles, mock(RolePermissionMapper::class.java),
        mock(RolePermissionProfileMapper::class.java), mock(PasswordHasher::class.java),
        mock(TokenService::class.java), AuthUserCache(properties), LoginThrottle(properties),
        mock(MenuService::class.java), mock(AuditService::class.java), ClientIp(properties), properties,
    )
}

package com.atguigu.hrspring.auth

import com.atguigu.hrspring.audit.AuditService
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.ClientIp
import com.atguigu.hrspring.common.api.ErrorCode
import com.atguigu.hrspring.config.HrProperties
import com.atguigu.hrspring.dto.UserCreateRequest
import com.atguigu.hrspring.mapper.RoleMapper
import com.atguigu.hrspring.mapper.RolePermissionMapper
import com.atguigu.hrspring.mapper.RolePermissionProfileMapper
import com.atguigu.hrspring.mapper.UserMapper
import com.atguigu.hrspring.service.AuthService
import com.atguigu.hrspring.service.MenuService
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.transaction.PlatformTransactionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthAccountProtectionTest {
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
}

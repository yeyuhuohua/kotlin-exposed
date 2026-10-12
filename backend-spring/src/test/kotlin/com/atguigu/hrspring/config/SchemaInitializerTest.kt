package com.atguigu.hrspring.config

import com.atguigu.hrspring.auth.PasswordHasher
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaInitializerTest {
    private fun run(created: Int): List<String> {
        val jdbc = mock(JdbcTemplate::class.java)
        `when`(jdbc.update(READER_PROFILE)).thenReturn(created)
        val tx = mock(PlatformTransactionManager::class.java)
        `when`(tx.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(SimpleTransactionStatus())
        val runner = SchemaInitializer().schemaInitializerRunner(
            jdbc, mock(PasswordHasher::class.java), tx, true, "custom-admin", "test-password",
        )
        runner.run(DefaultApplicationArguments())
        return mockingDetails(jdbc).invocations.mapNotNull { it.arguments.firstOrNull() as? String }
    }

    @Test
    fun `已有权限档案即使没有任何授权也不重新播种`() {
        val sql = run(0)
        assertEquals(0, sql.count { it.startsWith("INSERT IGNORE INTO auth_role_permissions") })
    }

    @Test
    fun `只在首次创建READER档案时播种默认授权`() {
        assertTrue(run(1).any { it.startsWith("INSERT IGNORE INTO auth_role_permissions") })
    }

    companion object {
        private const val READER_PROFILE =
            "INSERT IGNORE INTO auth_role_permission_profiles (role_code, revision) VALUES ('READER', 0)"
    }
}

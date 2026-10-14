package com.atguigu.hrspring.entity

import com.atguigu.hrspring.mapper.AuditApiCallMapper
import com.atguigu.hrspring.mapper.AuditLoginRecordMapper
import com.atguigu.hrspring.mapper.DepartmentMapper
import com.atguigu.hrspring.mapper.EmployeeMapper
import com.atguigu.hrspring.mapper.LocationMapper
import com.atguigu.hrspring.mapper.RegionMapper
import com.atguigu.hrspring.mapper.TDeptMapper
import com.atguigu.hrspring.mapper.TEmpMapper
import com.atguigu.hrspring.mapper.UserMapper
import com.baomidou.mybatisplus.core.MybatisConfiguration
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator
import org.apache.ibatis.mapping.MappedStatement
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Statement
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrimaryKeyMappingTest {
    @Test
    fun `auto increment inserts omit unset ids and populate generated keys`() {
        val entities = listOf(
            UserEntity() to UserMapper::class.java,
            TDeptEntity() to TDeptMapper::class.java,
            TEmpEntity() to TEmpMapper::class.java,
            AuditLoginRecordEntity() to AuditLoginRecordMapper::class.java,
            AuditApiCallEntity() to AuditApiCallMapper::class.java,
        )
        for ((entity, mapper) in entities) {
            val insert = insertStatement(mapper)
            val meta = insert.configuration.newMetaObject(entity)
            assertNull(meta.getValue("id"), mapper.simpleName)
            assertFalse(Regex("\\bid\\b").containsMatchIn(insert.getBoundSql(entity).sql), mapper.simpleName)

            val longId = meta.getSetterType("id") == Long::class.javaObjectType
            val generatedId: Number = if (longId) 4_000_000_000L else 73
            val keys = mock(ResultSet::class.java)
            val metadata = mock(ResultSetMetaData::class.java)
            val statement = mock(Statement::class.java)
            `when`(statement.generatedKeys).thenReturn(keys)
            `when`(keys.metaData).thenReturn(metadata)
            `when`(metadata.columnCount).thenReturn(1)
            `when`(metadata.getColumnType(1)).thenReturn(if (longId) Types.BIGINT else Types.INTEGER)
            `when`(keys.next()).thenReturn(true, false)
            if (longId) {
                `when`(keys.getLong(1)).thenReturn(generatedId.toLong())
            } else {
                `when`(keys.getInt(1)).thenReturn(generatedId.toInt())
            }

            assertIs<Jdbc3KeyGenerator>(insert.keyGenerator).processBatch(insert, statement, entity)
            assertEquals(generatedId, meta.getValue("id"), mapper.simpleName)
        }
    }

    @Test
    fun `input ids remain bound insert parameters`() {
        val entities = listOf(
            Triple(DepartmentEntity().apply { departmentId = 73 }, DepartmentMapper::class.java, "departmentId"),
            Triple(EmployeeEntity().apply { employeeId = 73 }, EmployeeMapper::class.java, "employeeId"),
            Triple(LocationEntity().apply { locationId = 73 }, LocationMapper::class.java, "locationId"),
            Triple(RegionEntity().apply { regionId = 73 }, RegionMapper::class.java, "regionId"),
        )
        for ((entity, mapper, property) in entities) {
            val insert = insertStatement(mapper)
            val meta = insert.configuration.newMetaObject(entity)
            assertFalse(meta.getSetterType(property).isPrimitive, mapper.simpleName)
            assertTrue(insert.getBoundSql(entity).parameterMappings.any { it.property == property }, mapper.simpleName)
            assertEquals(73, meta.getValue(property), mapper.simpleName)
        }
    }

    private fun insertStatement(mapper: Class<*>): MappedStatement {
        val configuration = MybatisConfiguration()
        configuration.addMapper(mapper)
        return configuration.getMappedStatement("${mapper.name}.insert")
    }
}

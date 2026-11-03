package com.atguigu.hrspring.common.cache

import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheEpochTest {
    @Test
    fun `共享版本通过一次 multiGet 按原顺序组装`() {
        val redis = mock(StringRedisTemplate::class.java)
        @Suppress("UNCHECKED_CAST")
        val operations = mock(ValueOperations::class.java) as ValueOperations<String, String>
        `when`(redis.opsForValue()).thenReturn(operations)
        `when`(operations.multiGet(listOf(CacheEpoch.DATA_KEY, CacheEpoch.AUTH_KEY)))
            .thenReturn(listOf("data-token", "auth-token"))
        val epoch = CacheEpoch(redis)

        assertEquals("data-token:auth-token", epoch.current())
        verify(operations).multiGet(listOf(CacheEpoch.DATA_KEY, CacheEpoch.AUTH_KEY))
        verify(operations, never()).get(anyString())
        verify(operations).set(eq(CacheEpoch.DATA_KEY), anyString())
    }
}

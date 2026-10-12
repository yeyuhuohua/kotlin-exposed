package com.atguigu.hrspring.auth

import com.atguigu.hrspring.common.cache.CacheEpoch
import com.atguigu.hrspring.config.HrProperties
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import kotlin.test.Test
import kotlin.test.assertNull

class SharedAuthInvalidationTest {
    @Test
    fun `其他后端的撤销及本地广播都清理旧鉴权缓存`() {
        var epoch = 0L
        val redis = mock(StringRedisTemplate::class.java)
        @Suppress("UNCHECKED_CAST")
        val values = mock(ValueOperations::class.java) as ValueOperations<String, String>
        `when`(redis.opsForValue()).thenReturn(values)
        `when`(values.get(CacheEpoch.AUTH_KEY)).thenAnswer { epoch.toString() }
        `when`(values.increment(CacheEpoch.AUTH_KEY)).thenAnswer { ++epoch }
        val cache = AuthUserCache(HrProperties(), SharedAuthInvalidation(redis))
        val user = AuthUser(7, "reader", "READER", true, true, 0, emptySet())
        assertNull(cache.get(7, 0))
        cache.put(7, 0, user, cache.generation())
        epoch++
        assertNull(cache.get(7, 0))
        cache.put(7, 0, user, cache.generation())
        // 本实例广播不得把并发发生的远程变更标成已处理。
        epoch++
        cache.invalidateUser(8)
        assertNull(cache.get(7, 0))
    }
}

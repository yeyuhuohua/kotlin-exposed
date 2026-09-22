package com.atguigu.hr.health

import com.atguigu.hr.config.DatabaseFactory
import com.atguigu.hr.config.RedisFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** 并发检查 MySQL 与 Redis 连通性，不依赖具体业务表是否有记录。 */
object HealthService {
    suspend fun check(): HealthDto = coroutineScope {
        val dbOk = async { DatabaseFactory.ping() }
        val redisOk = async { RedisFactory.ping() }
        val dbUp = dbOk.await()
        val redisUp = redisOk.await()
        HealthDto(
            status = if (dbUp && redisUp) "UP" else "DOWN",
            database = if (dbUp) "UP" else "DOWN",
            redis = if (redisUp) "UP" else "DOWN",
        )
    }
}

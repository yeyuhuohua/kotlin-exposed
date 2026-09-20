package com.atguigu.hr.health

import com.atguigu.hr.config.DatabaseFactory
import com.atguigu.hr.config.RedisFactory

/** 并发检查 MySQL 与 Redis 连通性，不依赖具体业务表是否有记录。 */

object HealthService {
    suspend fun check(): HealthDto {
        val dbOk = DatabaseFactory.ping()
        val redisOk = RedisFactory.ping()
        return HealthDto(
            status = if (dbOk && redisOk) "UP" else "DOWN",
            database = if (dbOk) "UP" else "DOWN",
            redis = if (redisOk) "UP" else "DOWN",
        )
    }
}

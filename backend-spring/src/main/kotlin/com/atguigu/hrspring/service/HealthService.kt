package com.atguigu.hrspring.service
import com.atguigu.hrspring.dto.HealthDto
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class HealthService(
    private val dataSource: DataSource,
    private val redisConnectionFactory: RedisConnectionFactory,
) {
    /** 返回健康详情，以及依赖是否全部可用。 */
    fun check(): Pair<HealthDto, Boolean> {
        val database = runCatching {
            dataSource.connection.use { it.isValid(2) }
        }.getOrDefault(false)
        val redis = runCatching {
            redisConnectionFactory.connection.use { it.ping() != null }
        }.getOrDefault(false)
        val up = database && redis
        return HealthDto(
            status = if (up) "UP" else "DOWN",
            database = if (database) "UP" else "DOWN",
            redis = if (redis) "UP" else "DOWN",
        ) to up
    }
}

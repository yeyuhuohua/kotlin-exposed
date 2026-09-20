package com.atguigu.hr.config

import io.ktor.server.config.ApplicationConfig
import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

/** Exposed R2DBC 连接；查询走 suspendTransaction，不阻塞 Ktor 线程。 */
object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)
    lateinit var database: R2dbcDatabase
        private set

    fun connect(config: ApplicationConfig) {
        val host = config.property("database.host").getString()
        val port = config.property("database.port").getString()
        val name = config.property("database.name").getString()
        val user = config.property("database.user").getString()
        val password = config.property("database.password").getString()
        // 本地 Docker MySQL 一般未开 SSL；zeroDate 避免 0000-00-00 解码失败
        val url = "r2dbc:mysql://$host:$port/$name?sslMode=disabled&zeroDate=use_null"

        database = R2dbcDatabase.connect(
            url = url,
            driver = "mysql",
            user = user,
            password = password,
            databaseConfig = R2dbcDatabaseConfig {
                defaultMaxAttempts = 3
                defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
            },
        )
        log.info("Configured non-blocking R2DBC connection to {}", url)
    }

    /** 只读挂起事务。R2DBC Query 是 Flow，必须在事务内 collect / toList。 */
    suspend fun <T> dbQuery(block: suspend R2dbcTransaction.() -> T): T =
        suspendTransaction(db = database, readOnly = true, statement = block)

    /** 写事务，给 PUT/DELETE 用。 */
    suspend fun <T> dbUpdate(block: suspend R2dbcTransaction.() -> T): T =
        suspendTransaction(db = database, readOnly = false, statement = block)

    /** 只检查库连通性，不碰业务表。失败返回 false，不抛给调用方。 */
    suspend fun ping(): Boolean = runCatching {
        dbQuery { exec("SELECT 1") }
    }.isSuccess
}

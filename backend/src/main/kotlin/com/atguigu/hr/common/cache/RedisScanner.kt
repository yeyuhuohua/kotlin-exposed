package com.atguigu.hr.common.cache

import com.atguigu.hr.config.RedisFactory
import io.lettuce.core.KeyScanCursor
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import kotlinx.coroutines.future.await

/** 用 SCAN 游标遍历键空间，避免在请求路径上调用会阻塞 Redis 的 KEYS。 */
internal object RedisScanner {
    private const val SCAN_BATCH = 256L

    /** 使用游标扫描匹配模式的 Redis 键，避免 KEYS 阻塞。 */
    suspend fun scan(pattern: String): List<String> {
        val keys = mutableListOf<String>()
        var cursor: ScanCursor = ScanCursor.INITIAL
        do {
            val result: KeyScanCursor<String> = RedisFactory.async
                .scan(cursor, ScanArgs.Builder.matches(pattern).limit(SCAN_BATCH))
                .await()
            keys += result.keys
            cursor = result
        } while (!result.isFinished)
        return keys
    }
}

package com.atguigu.hrspring.service

import com.atguigu.hrspring.config.HrProperties
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes

/** 管理导出文件、下载凭据、实例锁及过期清理，不涉及文档内容生成。 */
internal class ExportFileStore(
    properties: HrProperties,
) {
    private val publicBaseUrl = properties.export.publicBaseUrl.trim().trimEnd('/')
    private val ttlSeconds = properties.export.ttlSeconds.coerceIn(60, 86_400)
    private val files = ConcurrentHashMap<String, StoredExport>()
    private val random = SecureRandom()
    private val instanceId = newId()
    private val base: Path = resolveBase(properties.export.directory)
    private val root: Path = base.resolve(instanceId)
    private var liveChannel: FileChannel? = null
    private var liveLock: FileLock? = null
    private val released = AtomicBoolean(false)
    private val shutdownHook = Thread { releaseInstanceLock() }

    init {
        var hookAdded = false
        try {
            Files.createDirectories(root)
            val livePath = root.resolve(LIVE_FILE)
            val channel = FileChannel.open(
                livePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            liveChannel = channel
            val lock = channel.tryLock()
                ?: throw IllegalStateException("export instance lock is unavailable")
            liveLock = lock
            val payload = ByteBuffer.wrap(ttlSeconds.toString().toByteArray(StandardCharsets.UTF_8))
            channel.truncate(0)
            channel.position(0)
            channel.write(payload)
            channel.force(true)
            Runtime.getRuntime().addShutdownHook(shutdownHook)
            hookAdded = true
            sweepOtherInstances()
            sweepOrphans()
        } catch (e: Exception) {
            releaseInstanceLock()
            if (hookAdded) {
                runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
            }
            throw e
        }
    }

    fun close() {
        releaseInstanceLock()
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    }

    private fun releaseInstanceLock() {
        if (!released.compareAndSet(false, true)) return
        try {
            liveLock?.takeIf { it.isValid }?.release()
        } catch (e: Exception) {
            // 锁已经放开
        }
        try {
            liveChannel?.takeIf { it.isOpen }?.close()
        } catch (e: Exception) {
            // 通道已经关闭
        }
    }

    fun sweepExports() {
        purgeExpired()
        sweepOrphans()
        sweepOtherInstances()
    }

    /** 凭据只和哈希比较。过期、口令不对或文件已丢都返回 null。 */
    fun open(id: String, token: String): StoredFile? {
        purgeExpired()
        val stored = files[id] ?: return null
        if (Instant.now().isAfter(stored.expiresAt) || !tokenMatches(token, stored.tokenHash)) {
            if (Instant.now().isAfter(stored.expiresAt)) remove(id, stored)
            return null
        }
        if (!Files.isRegularFile(stored.path)) {
            files.remove(id, stored)
            return null
        }
        return StoredFile(stored.filename, stored.mediaType, stored.path.readBytes())
    }

    fun store(
        filename: String,
        mediaType: String,
        bytes: ByteArray,
        rowCount: Int,
        truncated: Boolean,
    ): ExportResult {
        purgeExpired()
        val id = newId()
        val token = newToken()
        val path = root.resolve(id + extensionFor(mediaType))
        Files.write(path, bytes)
        val expiresAt = Instant.now().plusSeconds(ttlSeconds)
        files[id] = StoredExport(sha256(token), path, filename, mediaType, expiresAt)
        val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
        return ExportResult(
            filename = filename,
            downloadUrl = "$publicBaseUrl/api/exports/$id?token=$encodedToken",
            expiresAt = DateTimeFormatter.ISO_INSTANT.format(expiresAt),
            rowCount = rowCount,
            truncated = truncated,
        )
    }

    private fun extensionFor(mediaType: String): String = when (mediaType) {
        DocumentExportService.XLSX -> ".xlsx"
        DocumentExportService.DOCX -> ".docx"
        DocumentExportService.PDF -> ".pdf"
        else -> error("unknown export type")
    }

    private fun purgeExpired() {
        val now = Instant.now()
        for ((id, stored) in files) {
            if (now.isAfter(stored.expiresAt)) remove(id, stored)
        }
    }

    /** 只清理本实例目录里、由本服务命名的文件。 */
    private fun sweepOrphans() {
        val live = files.values.mapTo(mutableSetOf()) { it.path.fileName.toString() }
        sweepOwnedFiles(root, Instant.now().minusSeconds(ORPHAN_GRACE_SECONDS), live)
    }

    /**
     * 其他实例目录只在确认进程已退出后清理，并使用该实例自己写下的 TTL。
     * 仍持有锁的目录，包括还没写入文件的空目录，一律不动。
     */
    private fun sweepOtherInstances() {
        withSweepLock {
            val listed = try {
                Files.list(base)
            } catch (e: NoSuchFileException) {
                return@withSweepLock
            } catch (e: Exception) {
                return@withSweepLock
            }
            listed.use { stream ->
                stream.forEach { dir ->
                    try {
                        if (!Files.isDirectory(dir)) return@forEach
                        val name = dir.fileName.toString()
                        if (name == instanceId || !INSTANCE_DIR.matches(name)) return@forEach
                        if (instanceIsAlive(dir)) return@forEach
                        sweepOwnedFiles(dir, Instant.now().minusSeconds(recordedTtl(dir)), emptySet())
                        if (onlyMarkerLeft(dir)) {
                            dir.resolve(LIVE_FILE).deleteIfExists()
                            dir.deleteIfExists()
                        }
                    } catch (e: NoSuchFileException) {
                        // 目录刚被其他实例删掉
                    } catch (e: DirectoryNotEmptyException) {
                        // 对方刚写入新文件
                    }
                }
            }
        }
    }

    private fun withSweepLock(block: () -> Unit) {
        synchronized(sweepMonitor) {
            val lockPath = base.resolve(CLEANUP_LOCK)
            try {
                FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                ).use { channel ->
                    val lock = channel.lock()
                    try {
                        block()
                    } finally {
                        try {
                            lock.release()
                        } catch (e: Exception) {
                            // 锁已经放开
                        }
                    }
                }
            } catch (e: NoSuchFileException) {
                // 导出根目录已经不在
            }
        }
    }

    private fun instanceIsAlive(dir: Path): Boolean {
        val live = dir.resolve(LIVE_FILE)
        if (!Files.isRegularFile(live)) {
            return try {
                Files.getLastModifiedTime(dir).toInstant()
                    .isAfter(Instant.now().minusSeconds(ORPHAN_GRACE_SECONDS))
            } catch (e: Exception) {
                true
            }
        }
        return try {
            FileChannel.open(live, StandardOpenOption.WRITE).use { channel ->
                val lock = channel.tryLock()
                if (lock == null) {
                    true
                } else {
                    lock.release()
                    false
                }
            }
        } catch (e: OverlappingFileLockException) {
            true
        } catch (e: Exception) {
            true
        }
    }

    private fun recordedTtl(dir: Path): Long =
        try {
            Files.readString(dir.resolve(LIVE_FILE)).trim().toLong().coerceIn(60, 86_400)
        } catch (e: Exception) {
            86_400
        }

    private fun onlyMarkerLeft(dir: Path): Boolean =
        try {
            Files.list(dir).use { stream ->
                stream.allMatch { it.fileName.toString() == LIVE_FILE }
            }
        } catch (e: NoSuchFileException) {
            false
        }

    private fun sweepOwnedFiles(dir: Path, cutoff: Instant, live: Set<String>) {
        val listed = try {
            Files.list(dir)
        } catch (e: NoSuchFileException) {
            return
        } catch (e: Exception) {
            return
        }
        listed.use { stream ->
            stream.forEach { path ->
                try {
                    if (!Files.isRegularFile(path)) return@forEach
                    val name = path.fileName.toString()
                    if (!OWNED_FILE.matches(name) || name in live) return@forEach
                    val modified = Files.getLastModifiedTime(path).toInstant()
                    if (modified.isBefore(cutoff)) path.deleteIfExists()
                } catch (e: NoSuchFileException) {
                    // 文件刚被其他实例删掉
                }
            }
        }
    }

    private fun remove(id: String, stored: StoredExport) {
        if (files.remove(id, stored)) stored.path.deleteIfExists()
    }

    private fun tokenMatches(token: String, expected: ByteArray): Boolean =
        MessageDigest.isEqual(sha256(token), expected)

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))

    private fun newId(): String = buildString(32) {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        bytes.forEach { append("%02x".format(it)) }
    }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun resolveBase(configured: String): Path {
        val path = if (configured.isBlank()) {
            Path.of(System.getProperty("java.io.tmpdir"), "hr-spring-exports")
        } else {
            Path.of(configured)
        }
        return try {
            Files.createDirectories(path)
        } catch (e: Exception) {
            throw IllegalStateException("export directory is unavailable")
        }
    }

    private data class StoredExport(
        val tokenHash: ByteArray,
        val path: Path,
        val filename: String,
        val mediaType: String,
        val expiresAt: Instant,
    )

    companion object {
        private const val ORPHAN_GRACE_SECONDS = 60L
        private val INSTANCE_DIR = Regex("[0-9a-f]{32}")
        private val OWNED_FILE = Regex("[0-9a-f]{32}\\.(xlsx|docx|pdf)")
        private const val LIVE_FILE = ".live"
        private const val CLEANUP_LOCK = ".cleanup.lock"
        private val sweepMonitor = Any()
    }
}

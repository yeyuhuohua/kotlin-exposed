package com.atguigu.hrspring.audit

import com.atguigu.hrspring.mapper.AuditApiCallMapper
import com.atguigu.hrspring.mapper.AuditLoginRecordMapper
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditServiceTest {

    private val loginInserts = AtomicInteger()

    private fun service(): AuditService = AuditService(
        fakeMapper(AuditLoginRecordMapper::class.java, loginInserts),
        fakeMapper(AuditApiCallMapper::class.java, AtomicInteger()),
    )

    private fun <T> fakeMapper(type: Class<T>, inserts: AtomicInteger): T {
        val handler = InvocationHandler { _, method, _ ->
            if (method.name == "insert") {
                inserts.incrementAndGet()
                1
            } else {
                defaultValue(method.returnType)
            }
        }
        return type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler),
        )
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        Character.TYPE -> 0.toChar()
        else -> null
    }

    private fun droppedOf(service: AuditService): Long {
        val field = AuditService::class.java.getDeclaredField("dropped")
        field.isAccessible = true
        return (field.get(service) as AtomicLong).get()
    }

    @Test
    fun `队列满时丢弃且不阻塞请求线程`() {
        val s = service()
        // 不调用 start():消费者不存在,队列只进不出,第 1001 条必须走丢弃路径
        val started = System.nanoTime()
        repeat(AuditService.QUEUE_CAPACITY + 1) {
            s.recordLogin("user$it", it, "127.0.0.1", "agent", true, null)
        }
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMillis < 5_000, "recordLogin 不能阻塞,实际耗时 ${elapsedMillis}ms")
        assertEquals(1, droppedOf(s))
        assertEquals(0, loginInserts.get())
        s.shutdown()
    }

    @Test
    fun `停机时排空队列在途记录全部落库`() {
        val s = service()
        s.start()
        repeat(50) {
            s.recordLogin("user$it", it, "127.0.0.1", "agent", true, null)
        }
        // shutdown() 会 join 消费者线程,返回即代表队列已排空,无需 sleep
        s.shutdown()
        assertEquals(50, loginInserts.get())
    }

    @Test
    fun `停机后拒绝入队不再产生写入`() {
        val s = service()
        s.start()
        s.shutdown()
        s.recordLogin("late", 1, "127.0.0.1", "agent", true, null)
        assertEquals(0, loginInserts.get())
        assertEquals(0, droppedOf(s))
    }
}

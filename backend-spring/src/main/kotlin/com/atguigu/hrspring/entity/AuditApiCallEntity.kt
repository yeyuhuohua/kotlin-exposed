package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime
@TableName("audit_api_calls")
class AuditApiCallEntity {
    @TableId(value = "id", type = IdType.AUTO)
    var id: Long = 0L
    var userId: Int? = null
    var username: String? = null
    var method: String = ""
    var path: String = ""
    var queryString: String? = null
    var statusCode: Int = 0
    var durationMs: Long = 0L
    var ip: String = ""
    var userAgent: String? = null
    var createdAt: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)
}

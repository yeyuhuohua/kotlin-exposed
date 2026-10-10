package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime
@TableName("audit_login_records")
class AuditLoginRecordEntity {
    @TableId(value = "id", type = IdType.AUTO)
    var id: Long = 0L
    var username: String = ""
    var userId: Int? = null
    var ip: String = ""
    var userAgent: String? = null
    var success: Boolean = false
    var errorCode: String? = null
    var createdAt: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)
}

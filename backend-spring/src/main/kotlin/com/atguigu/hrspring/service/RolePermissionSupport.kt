package com.atguigu.hrspring.service

import com.atguigu.hrspring.auth.PermissionCatalog
import com.atguigu.hrspring.auth.RoleDto
import com.atguigu.hrspring.common.api.ErrorCode
import com.atguigu.hrspring.entity.RoleEntity
import org.springframework.dao.DataIntegrityViolationException

/** 过滤不存在或不允许授予目标角色的权限码。 */
internal fun grantable(code: String): Boolean =
    code.startsWith("page:") || PermissionCatalog.byCode[code]?.adminOnly == false

/** 把角色授权失败转换为稳定的业务错误。 */
internal fun grantError(code: String, menuAdminOnlyByKey: Map<String, Boolean>): String? {
    val definition = PermissionCatalog.byCode[code]
    if (definition != null) return if (definition.adminOnly) ErrorCode.ADMIN_ONLY else null
    if (code.startsWith("page:")) {
        val adminOnly = menuAdminOnlyByKey[code.removePrefix("page:")] ?: return ErrorCode.UNKNOWN_PERMISSION
        return if (adminOnly) ErrorCode.ADMIN_ONLY else null
    }
    return ErrorCode.UNKNOWN_PERMISSION
}

/** 判断异常是否由数据库约束冲突引起。 */
internal fun Throwable.isConstraintConflict(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is DataIntegrityViolationException) return true
        if ("Duplicate entry" in current.message.orEmpty()) return true
        current = current.cause
    }
    return false
}

/** 将角色权限数据转换为对外传输对象。 */
internal fun RoleEntity.toDto(): RoleDto = RoleDto(code, name, enabled)

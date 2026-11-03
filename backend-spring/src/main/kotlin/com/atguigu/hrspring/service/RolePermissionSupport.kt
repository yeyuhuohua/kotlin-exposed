package com.atguigu.hrspring.service

import com.atguigu.hrspring.auth.PermissionCatalog
import com.atguigu.hrspring.auth.RoleDto
import com.atguigu.hrspring.common.api.ErrorCode
import com.atguigu.hrspring.entity.RoleEntity
import org.springframework.dao.DataIntegrityViolationException

internal fun grantable(code: String): Boolean =
    code.startsWith("page:") || PermissionCatalog.byCode[code]?.adminOnly == false

internal fun grantError(code: String, menuAdminOnlyByKey: Map<String, Boolean>): String? {
    val definition = PermissionCatalog.byCode[code]
    if (definition != null) return if (definition.adminOnly) ErrorCode.ADMIN_ONLY else null
    if (code.startsWith("page:")) {
        val adminOnly = menuAdminOnlyByKey[code.removePrefix("page:")] ?: return ErrorCode.UNKNOWN_PERMISSION
        return if (adminOnly) ErrorCode.ADMIN_ONLY else null
    }
    return ErrorCode.UNKNOWN_PERMISSION
}

internal fun Throwable.isConstraintConflict(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is DataIntegrityViolationException) return true
        if ("Duplicate entry" in current.message.orEmpty()) return true
        current = current.cause
    }
    return false
}

internal fun RoleEntity.toDto(): RoleDto = RoleDto(code, name, enabled)

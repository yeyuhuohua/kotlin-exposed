package com.atguigu.hrspring.auth

import com.atguigu.hrspring.common.api.ApiException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 权限拦截器:登录接口除外的 /api 路径必须已认证;
 * 按 PermissionCatalog 匹配权限码,未登记的受保护接口默认拒绝。
 */
@Component
class PermissionInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val path = request.requestURI
        if (!path.startsWith("/api/")) return true
        if (path == "/api/health" || path == "/api/auth/login") return true

        val user = CurrentUserHolder.get()
            ?: throw ApiException.unauthorized()
        if (PermissionCatalog.isLoginOnly(path)) return true

        val definition = PermissionCatalog.requiredApi(request.method, path)
            ?: throw ApiException.forbidden()
        if (!user.hasPermission(definition.code)) {
            throw ApiException.forbidden()
        }
        return true
    }
}

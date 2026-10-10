package com.atguigu.hrspring.auth

/**
 * 鉴权过滤器与缓存之间的接缝,由 AuthService 实现。
 */
interface AuthUserLoader {
    /** 加载活跃用户(账号与角色均启用)且 tokenVersion 与 JWT 一致;否则返回 null。 */
    fun loadActiveUser(userId: Int, tokenVersion: Int): AuthUser?

    /** 加载启用中的内置管理员，供本机 MCP 服务口令使用。 */
    fun loadActiveAdmin(): AuthUser?
}

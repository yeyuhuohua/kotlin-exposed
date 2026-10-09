package com.atguigu.hr.menu

import com.atguigu.hr.common.api.respondFail
import com.atguigu.hr.common.api.respondOk
import com.atguigu.hr.docs.requestExample
import com.atguigu.hr.docs.responseExamples
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

/** 菜单管理接口：页面清单的运行时来源，全部仅 ADMIN 可调用（权限目录登记为 adminOnly）。 */

/** 这些入口停用后管理员会失去对应管理界面，不允许通过接口停用。 */
private val protectedMenuKeys = setOf("menus", "users", "roles")

@OptIn(ExperimentalKtorApi::class)
fun Route.menuRoutes() {
    route("/menus") {
        get {
            call.respondOk(MenuService.catalog())
        }.describe {
            summary = "查询全部菜单（含停用，仅 ADMIN）；当前用户可见菜单用 GET /api/auth/routes"
            tag("menu")
            responseExamples(listOf(sampleMenu, sampleMenu.copy(key = "orders", title = "示例订单", builtin = false)))
        }
        post {
            val body = call.receive<MenuCreateRequest>()
            body.contentError()?.let { return@post call.respondFail(HttpStatusCode.BadRequest, it) }
            call.respondOk(MenuService.create(body), message = "created")
        }.describe {
            summary = "新增菜单（仅 ADMIN）；key 与路径全局唯一，adminOnly 创建后不可再改"
            tag("menu")
            requestExample(sampleMenuCreate, "新菜单默认无任何角色授权，需在角色权限里分配后才可见")
            responseExamples(sampleMenu, message = "created",
                fails = arrayOf(HttpStatusCode.BadRequest to "menu key is reserved", HttpStatusCode.Conflict to "resource conflict"))
        }
        put("/{key}") {
            val key = call.parameters["key"]?.takeIf { validateMenuKey(it) == null }
                ?: return@put call.respondFail(HttpStatusCode.BadRequest, "invalid menu key")
            val body = call.receive<MenuUpdateRequest>()
            body.contentError()?.let { return@put call.respondFail(HttpStatusCode.BadRequest, it) }
            if (key in protectedMenuKeys && !body.enabled) {
                return@put call.respondFail(HttpStatusCode.BadRequest, "management entry cannot be disabled")
            }
            val updated = MenuService.update(key, body)
                ?: return@put call.respondFail(HttpStatusCode.NotFound, "menu not found")
            call.respondOk(updated, message = "updated")
        }.describe {
            summary = "整体替换菜单的标题、路径、图标、分组、排序与状态（仅 ADMIN）"
            tag("menu")
            parameters { path("key") { description = "菜单 key" } }
            requestExample(sampleMenuUpdate, "adminOnly 与 builtin 不可修改；菜单管理、用户管理、角色权限入口不可停用")
            responseExamples(sampleMenu, message = "updated",
                fails = arrayOf(HttpStatusCode.BadRequest to "invalid menu update", HttpStatusCode.NotFound to "menu not found"))
        }
        delete("/{key}") {
            val key = call.parameters["key"]?.takeIf { validateMenuKey(it) == null }
                ?: return@delete call.respondFail(HttpStatusCode.BadRequest, "invalid menu key")
            val menu = MenuService.catalog().firstOrNull { it.key == key }
                ?: return@delete call.respondFail(HttpStatusCode.NotFound, "menu not found")
            if (menu.builtin) {
                return@delete call.respondFail(HttpStatusCode.Forbidden, "builtin menu is protected")
            }
            MenuService.delete(key)
            call.respondOk("deleted")
        }.describe {
            summary = "删除菜单并清理各角色持有的页面授权（仅 ADMIN）；内置菜单不可删除"
            tag("menu")
            parameters { path("key") { description = "菜单 key" } }
            responseExamples("deleted", fails = arrayOf(
                HttpStatusCode.Forbidden to "builtin menu is protected",
                HttpStatusCode.NotFound to "menu not found",
            ))
        }
    }
}

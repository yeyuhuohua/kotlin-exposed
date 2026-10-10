package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.body
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.dto.MenuCreateRequest
import com.atguigu.hrspring.dto.MenuDto
import com.atguigu.hrspring.dto.MenuUpdateRequest
import com.atguigu.hrspring.service.MenuService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** 菜单管理。当前用户可见菜单由认证模块调 MenuService.pagesFor。 */
@Tag(name = "menu")
@RestController
class MenuController(
    private val menuService: MenuService,
) {
    @Operation(summary = "查询全部菜单（含停用，仅 ADMIN）；当前用户可见菜单用 GET /api/auth/routes")
    @GetMapping("/api/menus")
    fun list(): ResponseEntity<ApiResult<List<MenuDto>>> {
        val (data, hit) = menuService.list()
        return cachedOk(data, hit)
    }

    @Operation(summary = "新增菜单（仅 ADMIN）；key 与路径全局唯一，adminOnly 创建后不可再改")
    @PostMapping("/api/menus")
    fun create(@RequestBody request: MenuCreateRequest): ResponseEntity<ApiResult<MenuDto>> =
        body(menuService.create(request), "created")

    @Operation(summary = "整体替换菜单的标题、路径、图标、分组、排序与状态（仅 ADMIN）")
    @PutMapping("/api/menus/{key}")
    fun update(
        @PathVariable key: String,
        @RequestBody request: MenuUpdateRequest,
    ): ResponseEntity<ApiResult<MenuDto>> = body(menuService.update(key, request), "updated")

    @Operation(summary = "删除菜单并清理各角色持有的页面授权（仅 ADMIN）；内置菜单不可删除")
    @DeleteMapping("/api/menus/{key}")
    fun delete(@PathVariable key: String): ResponseEntity<ApiResult<String>> {
        menuService.delete(key)
        return body("deleted", "ok")
    }
}

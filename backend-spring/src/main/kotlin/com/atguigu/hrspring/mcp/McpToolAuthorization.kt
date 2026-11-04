package com.atguigu.hrspring.mcp

import com.atguigu.hrspring.auth.AuthUser
import com.atguigu.hrspring.auth.CurrentUserHolder
import com.atguigu.hrspring.auth.PermissionCatalog
import com.atguigu.hrspring.common.api.ApiException
import io.modelcontextprotocol.spec.McpError
import jakarta.validation.ConstraintViolationException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext
import org.springframework.stereotype.Component
import org.springframework.validation.method.MethodValidationException
import java.util.concurrent.CancellationException

/** MCP 工具与 HTTP 接口共用权限码；未登记的工具默认拒绝。 */
object McpToolPermissions {
    private data class ApiGrant(val method: String, val path: String)

    private fun api(method: String, path: String) = ApiGrant(method, path)

    private val loginOnly = setOf(
        "get_current_user",
        "list_page_routes",
        "get_health",
        "export_excel",
        "export_word",
        "export_pdf",
    )

    private val required = buildMap {
        put("get_overview", api("GET", "/overview"))
        put("list_employees", api("GET", "/employees"))
        put("create_employee", api("POST", "/employees"))
        put("get_employee", api("GET", "/employees/{id}"))
        put("update_employee", api("PUT", "/employees/{id}"))
        put("patch_employee", api("PATCH", "/employees/{id}"))
        put("delete_employee", api("DELETE", "/employees/{id}"))
        put("get_employee_details", api("GET", "/employees/{id}/details"))
        put("list_emp_details", api("GET", "/emp-details"))
        put("list_departments", api("GET", "/departments"))
        put("create_department", api("POST", "/departments"))
        put("get_department", api("GET", "/departments/{id}"))
        put("update_department", api("PUT", "/departments/{id}"))
        put("list_department_employees", api("GET", "/departments/{id}/employees"))
        put("list_jobs", api("GET", "/jobs"))
        put("create_job", api("POST", "/jobs"))
        put("update_job", api("PUT", "/jobs/{id}"))
        put("list_locations", api("GET", "/locations"))
        put("create_location", api("POST", "/locations"))
        put("update_location", api("PUT", "/locations/{id}"))
        put("list_countries", api("GET", "/countries"))
        put("list_regions", api("GET", "/regions"))
        put("list_job_history", api("GET", "/job-history"))
        put("list_job_grades", api("GET", "/job-grades"))
        put("list_t_dept", api("GET", "/t-dept"))
        put("create_t_dept", api("POST", "/t-dept"))
        put("update_t_dept", api("PUT", "/t-dept/{id}"))
        put("list_t_emp", api("GET", "/t-emp"))
        put("create_t_emp", api("POST", "/t-emp"))
        put("update_t_emp", api("PUT", "/t-emp/{id}"))
        put("list_orders", api("GET", "/orders"))
        put("list_users", api("GET", "/auth/users"))
        put("create_user", api("POST", "/auth/users"))
        put("update_user", api("PUT", "/auth/users/{id}"))
        put("delete_user", api("DELETE", "/auth/users/{id}"))
        put("list_roles", api("GET", "/auth/roles"))
        put("create_role", api("POST", "/auth/roles"))
        put("update_role", api("PUT", "/auth/roles/{code}"))
        put("delete_role", api("DELETE", "/auth/roles/{code}"))
        put("list_permissions", api("GET", "/auth/permissions"))
        put("get_role_permissions", api("GET", "/auth/roles/{code}/permissions"))
        put("update_role_permissions", api("PUT", "/auth/roles/{code}/permissions"))
        put("list_login_records", api("GET", "/audit/logins"))
        put("list_api_calls", api("GET", "/audit/api-calls"))
        put("list_menus", api("GET", "/menus"))
        put("create_menu", api("POST", "/menus"))
        put("update_menu", api("PUT", "/menus/{key}"))
        put("delete_menu", api("DELETE", "/menus/{key}"))
    }

    fun isAllowed(toolName: String, user: AuthUser): Boolean {
        if (!user.active) return false
        if (toolName in loginOnly) return true
        val permission = permissionCode(toolName) ?: return false
        return user.hasPermission(permission)
    }

    internal fun permissionCode(toolName: String): String? = required[toolName]?.let { grant ->
        PermissionCatalog.requiredApi(grant.method, "/api${grant.path}")?.code
    }

    fun authorize(toolName: String) {
        val user = CurrentUserHolder.require()
        if (!isAllowed(toolName, user)) throw ApiException.forbidden()
    }
}

/** MCP 回调可能运行在其他线程；身份随 McpTransportContext 传递，只在工具执行期间写入本线程。 */
@Aspect
@Component
class McpToolAuthorizationAspect {
    private val log = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(tool)")
    fun authorize(joinPoint: ProceedingJoinPoint, tool: McpTool): Any? {
        val context = joinPoint.args.filterIsInstance<McpSyncRequestContext>().firstOrNull()
        if (context == null) return invoke(joinPoint, tool)
        val user = context.exchange().transportContext().get(McpSecurityConfig.MCP_AUTHENTICATED_USER) as? AuthUser
        CurrentUserHolder.set(user)
        return try {
            invoke(joinPoint, tool)
        } finally {
            CurrentUserHolder.clear()
        }
    }

    private fun invoke(joinPoint: ProceedingJoinPoint, tool: McpTool): Any? {
        McpToolPermissions.authorize(tool.name)
        return try {
            joinPoint.proceed()
        } catch (error: McpError) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            throw error
        } catch (error: ConstraintViolationException) {
            throw error
        } catch (error: MethodValidationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            log.error("MCP tool {} execution failed", tool.name, error)
            throw ApiException(500, "internal error")
        }
    }
}

package com.atguigu.hrspring.common.api

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.server.PathContainer
import org.springframework.web.util.ServletRequestPathUtils

/** 与 MVC 路由使用相同的路径段，排除 context/servlet 前缀及路径参数。 */
fun HttpServletRequest.applicationPath(): String {
    val path = if (ServletRequestPathUtils.hasParsedRequestPath(this)) {
        ServletRequestPathUtils.getParsedRequestPath(this)
    } else {
        ServletRequestPathUtils.parse(this)
    }
    return path.pathWithinApplication().elements().joinToString("") { element ->
        if (element is PathContainer.PathSegment) element.valueToMatch() else element.value()
    }
}

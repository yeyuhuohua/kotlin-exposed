package com.atguigu.hr.common.api

/**
 * Exposed 的 varchar/char 按 Unicode 码点计长，而 String.length 按 UTF-16 单元计数，
 * 增补平面字符（如 𠮷）会被多数一个。长度校验必须与此保持一致。
 */
fun String.exceedsMaxLength(max: Int): Boolean = codePointCount(0, length) > max

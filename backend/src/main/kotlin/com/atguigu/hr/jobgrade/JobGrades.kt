package com.atguigu.hr.jobgrade

import org.jetbrains.exposed.v1.core.Table

/** 薪资等级表的 Exposed 映射；字段类型、长度和可空性对应现有 MySQL 结构。 */

object JobGrades : Table("job_grades") { // 薪资等级
    val gradeLevel = varchar("grade_level", 3).nullable()
    val lowestSal = integer("lowest_sal").nullable()
    val highestSal = integer("highest_sal").nullable()
}

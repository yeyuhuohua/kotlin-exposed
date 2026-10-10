package com.atguigu.hrspring.mapper
import com.atguigu.hrspring.entity.EmployeeEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface EmployeeMapper : BaseMapper<EmployeeEntity> {
    /** 按部门人数聚合，含未分配部门。departmentName 为空表示未分配。 */
    @Select(
        """
        SELECT e.department_id AS departmentId,
               d.department_name AS departmentName,
               COUNT(e.employee_id) AS headcount
        FROM employees e
        LEFT JOIN departments d ON e.department_id = d.department_id
        GROUP BY e.department_id, d.department_name
        ORDER BY headcount DESC
        """,
    )
    fun countByDepartment(): List<Map<String, Any>>

    /**
     * 员工 INNER JOIN 岗位，部门及以下 LEFT JOIN。
     * departmentRowId 来自 departments，用来区分「员工挂了已删除的部门」和「未分配」。
     */
    @Select(
        """
        SELECT e.employee_id AS employeeId,
               e.first_name AS firstName,
               e.last_name AS lastName,
               e.email AS email,
               e.phone_number AS phoneNumber,
               DATE_FORMAT(e.hire_date, '%Y-%m-%d') AS hireDate,
               e.job_id AS jobId,
               j.job_title AS jobTitle,
               e.salary AS salary,
               e.commission_pct AS commissionPct,
               e.manager_id AS managerId,
               e.department_id AS departmentId,
               d.department_id AS departmentRowId,
               d.department_name AS departmentName,
               l.location_id AS locationId,
               l.city AS city,
               l.state_province AS stateProvince,
               c.country_id AS countryId,
               c.country_name AS countryName,
               r.region_name AS regionName
        FROM employees e
        INNER JOIN jobs j ON e.job_id = j.job_id
        LEFT JOIN departments d ON e.department_id = d.department_id
        LEFT JOIN locations l ON d.location_id = l.location_id
        LEFT JOIN countries c ON l.country_id = c.country_id
        LEFT JOIN regions r ON c.region_id = r.region_id
        WHERE e.employee_id = #{id}
        """,
    )
    fun findDetail(@Param("id") id: Int): Map<String, Any>?
}

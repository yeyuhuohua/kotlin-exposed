package com.atguigu.hrspring.mapper
import com.atguigu.hrspring.entity.JobEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper
@Mapper
interface JobMapper : BaseMapper<JobEntity>

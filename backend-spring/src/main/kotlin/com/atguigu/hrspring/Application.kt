package com.atguigu.hrspring

import org.apache.ibatis.annotations.Mapper
import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@MapperScan(basePackages = ["com.atguigu.hrspring"], annotationClass = Mapper::class)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args) {
        setDefaultProperties(
            mapOf(
                "spring.config.additional-location" to
                    "optional:file:backend-spring/src/main/resources/application.yaml,optional:file:src/main/resources/application.yaml",
            ),
        )
    }
}

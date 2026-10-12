package com.atguigu.hrspring.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.cfg.CoercionAction as Jackson2CoercionAction
import com.fasterxml.jackson.databind.cfg.CoercionInputShape as Jackson2CoercionInputShape
import com.fasterxml.jackson.databind.type.LogicalType as Jackson2LogicalType
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.type.LogicalType

/**
 * Spring Boot 4 的 HTTP 层用 Jackson 3，不会再提供
 * com.fasterxml.jackson.databind.ObjectMapper。过滤器、缓存仍使用 Jackson 2。
 */
@Configuration
class JacksonConfig {
    @Bean
    fun strictJsonTypes(): JsonMapperBuilderCustomizer = JsonMapperBuilderCustomizer { builder ->
        builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        builder.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        builder.withCoercionConfig(LogicalType.Textual) { coercion ->
            coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
        }
    }

    @Bean
    fun objectMapper(): ObjectMapper =
        jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .apply {
                coercionConfigFor(Jackson2LogicalType.Textual).apply {
                    setCoercion(Jackson2CoercionInputShape.Integer, Jackson2CoercionAction.Fail)
                    setCoercion(Jackson2CoercionInputShape.Float, Jackson2CoercionAction.Fail)
                    setCoercion(Jackson2CoercionInputShape.Boolean, Jackson2CoercionAction.Fail)
                }
            }
}

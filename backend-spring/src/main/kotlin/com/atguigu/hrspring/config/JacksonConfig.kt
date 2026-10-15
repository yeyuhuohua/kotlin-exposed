package com.atguigu.hrspring.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.type.LogicalType

/** HTTP 使用的 Jackson 3 JsonMapper。字符串字段不接受数字或布尔。 */
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
}

plugins {
    kotlin("jvm") version "2.4.21"
    // 让 @Configuration、@Service 等 Spring 注解类对 CGLIB 代理开放。
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "4.1.1"
    // 统一 Spring 家族依赖版本，下面的 starter 不再手写版本号。
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.atguigu"
version = "0.0.1"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // 保留参数名，Spring MVC 和校验才能按方法参数绑定。
        freeCompilerArgs.add("-java-parameters")
    }
}

dependencyManagement {
    imports {
        // Spring AI 的 starter 版本由这个 BOM 对齐，不在 dependencies 里单独写。
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.1")
    }
}

dependencies {
    // HTTP API。Boot 4 的 Web 栈是 starter-webmvc，不是旧的 starter-web。
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // Jakarta Bean Validation。HTTP 和 MCP 都走注解校验。
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // @Cacheable / @CacheEvict。
    implementation("org.springframework.boot:spring-boot-starter-cache")
    // 缓存的 Redis 实现。键前缀 hrs:，和 Ktor 的 hr: 分开。
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // MCP Streamable HTTP。工具从 @McpTool 扫描，入口是 /mcp。
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    // Boot 4 对应的 MyBatis-Plus。Mapper 只认 @Mapper，避免扫到别的接口。
    implementation("com.baomidou:mybatis-plus-spring-boot4-starter:3.5.17")
    // 只在运行时需要，编译期不引用驱动类。
    runtimeOnly("com.mysql:mysql-connector-j")

    // 登录 JWT。
    implementation("com.auth0:java-jwt:4.6.1")
    // Excel。FastExcel 已捐给 Apache 并改名为 Fesod，不要再引入 fastexcel。
    implementation("org.apache.fesod:fesod-sheet:2.1.0-incubating")
    // Word。不读模板文件，用 Documents.of() 直接组 docx。
    implementation("com.deepoove:poi-tl:1.12.2")
    // PDF。用 PDFBox 3 做子集嵌入和排版。
    implementation("org.apache.pdfbox:pdfbox:3.0.5")
    // Jackson 3。HTTP、过滤器、缓存和导出共用 tools.jackson。
    // JsonIgnore 等注解仍在 com.fasterxml.jackson.annotation，由 Jackson 3 传递依赖提供。
    implementation("tools.jackson.module:jackson-module-kotlin")
    // 配置绑定和 Kotlin 数据类需要 kotlin-reflect。
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Swagger UI 和 OpenAPI JSON。
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    // 切片测试和 MockMvc。导出测试不连 MySQL / Redis。
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
    // Gradle 需要这个启动器才能发现 JUnit 5。
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

tasks.processResources {
    // 本机 application.yaml 含密码和密钥，不打进构建产物。
    exclude("application.yaml")
}

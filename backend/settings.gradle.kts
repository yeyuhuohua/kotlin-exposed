pluginManagement {
    repositories {
        // mavenLocal() 会让本机 ~/.m2 的同名构件静默优先于远程仓库，破坏可复现性，不放进来。
        // central 放在 public 前：public 聚合仓的 Kotlin Gradle 插件变体 jar（如 gradle813）可能缺失，
        // 元数据命中后 Gradle 不会 fallback 到其他仓库，会直接导致解析失败。
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        mavenCentral()
    }
}

rootProject.name = "kotlin-exposed"

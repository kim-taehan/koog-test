plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.sonarqube") version "6.0.1.5171"
    id("jacoco")
}

group = "develop.x"
version = "1.0-SNAPSHOT"

// Spring Boot 3.4.1 BOM 의 kotlin-stdlib 핀 (2.0.x) 을 컴파일러 버전과 일치시킴.
// 안 그러면 stdlib 의 DebugMetadata reader 가 컴파일러가 emit 한 v2 metadata 를 못 읽음.
extra["kotlin.version"] = "2.2.0"

repositories {
    mavenCentral()
}

val koogVersion = "0.8.0"
val coroutinesVersion = "1.10.2"  // Kotlin 2.2 의 debug metadata v2 를 이해하는 최소 버전대

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // 명시적 버전 — Spring Boot 3.4.1 BOM 의 옛날 코루틴이 Kotlin 2.2 와 충돌해서 강제 override
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutinesVersion")
    // Spring Boot 3.4.1 BOM 이 kotlinx-serialization-*-jvm 을 1.6.3 으로 핀하는데, Koog 0.8
    // (Kotlin 2.2 serialization 플러그인) 의 GeneratedSerializer.typeParametersSerializers()
    // 디폴트 메서드가 1.7+ 에서만 제공돼서 BOM 을 우회해 강제 override (아래 resolutionStrategy 참고).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Koog — Ollama client + prompt model types (no agent runtime needed yet)
    implementation("ai.koog:prompt-executor-ollama-client:$koogVersion")
    implementation("ai.koog:prompt-model:$koogVersion")
    // Koog 내부 Ktor HttpClient 가 ServiceLoader 로 엔진을 찾으므로 하나 제공해야 함
    runtimeOnly("io.ktor:ktor-client-cio:3.2.2")

    // Swagger UI (springdoc-openapi for WebFlux)
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.6")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
}

// Spring Boot 3.4.1 BOM 의 dependency-management 가 kotlinx-serialization-*-jvm 을 1.6.3 으로
// 강제 다운그레이드하므로 모든 configuration 에서 1.8.1 로 고정.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlinx" &&
            requested.name.startsWith("kotlinx-serialization")) {
            useVersion("1.8.1")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
    }
}

sonarqube {
    properties {
        property("sonar.projectKey", "kim-taehan_koog-test")
        property("sonar.organization", "kim-taehan")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sources", "src/main/kotlin")
        property("sonar.tests", "src/test/kotlin")
        property("sonar.kotlin.detekt.reportPaths", "")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
    }
}

kotlin {
    jvmToolchain(21)
}

plugins {
    `java-library`
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.4")
    }
}

dependencies {
    api(project(":core"))
    implementation("org.springframework:spring-webmvc")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")

    // 오류 봉투가 실제로 라우팅되는지 모듈 안에서 검증하기 위한 MockMvc 최소 구성이다.
    testImplementation("org.springframework:spring-test")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("com.jayway.jsonpath:json-path")
    testImplementation("org.hamcrest:hamcrest")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

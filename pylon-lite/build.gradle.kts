plugins {
    `java-test-fixtures`
}

dependencies {
    api("org.springframework:spring-context")
    api("org.springframework:spring-web")
    api("org.apache.httpcomponents:httpclient")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("org.slf4j:slf4j-api")

    testFixturesApi("com.fasterxml.jackson.core:jackson-databind")
}

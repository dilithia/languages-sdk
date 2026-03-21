plugins {
    java
    `maven-publish`
    signing
}
group = "org.dilithia"
version = "0.3.0"
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}
dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("net.java.dev.jna:jna:5.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}
tasks.test { useJUnitPlatform() }
tasks.javadoc { options.encoding = "UTF-8" }

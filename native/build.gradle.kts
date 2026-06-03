plugins {
    `java-library`
    id("link-publish")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.netty.handler)
    implementation(libs.checker.qual)
}

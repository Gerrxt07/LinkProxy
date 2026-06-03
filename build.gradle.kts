plugins {
    `java-library`
    id("link-checkstyle") apply false
    id("link-spotless") apply false
}

subprojects {
    apply<JavaLibraryPlugin>()

    apply(plugin = "link-checkstyle")
    apply(plugin = "link-spotless")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("--enable-preview")
    }

    dependencies {
        testImplementation(rootProject.libs.junit)
    }

    testing.suites.named<JvmTestSuite>("test") {
        useJUnitJupiter()
        targets.all {
            testTask.configure {
                jvmArgs("--enable-preview")
                reports.junitXml.required = true
            }
        }
    }
}

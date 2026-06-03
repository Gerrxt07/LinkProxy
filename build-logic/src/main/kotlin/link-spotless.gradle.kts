import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin

apply<SpotlessPlugin>()

extensions.configure<SpotlessExtension> {
    java {
        if (project.name == "link-api") {
            licenseHeaderFile(file("HEADER.txt"))
            targetExclude("**/java/com/linkpowered/api/util/Ordered.java")
        } else {
            licenseHeaderFile(rootProject.file("HEADER.txt"))
        }
        removeUnusedImports()
    }
}

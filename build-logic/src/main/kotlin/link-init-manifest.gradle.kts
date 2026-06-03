import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.withType
import java.io.ByteArrayOutputStream

// This interface is needed as a workaround to get an instance of ExecOperations
interface Injected {
    @get:Inject
    val execOps: ExecOperations
}

val currentShortRevision = ByteArrayOutputStream().use {
    val errorOutput = ByteArrayOutputStream()
    val execOps = objects.newInstance<Injected>().execOps
    val result = execOps.exec {
        executable = "git"
        args = listOf("rev-parse", "HEAD")
        standardOutput = it
        setErrorOutput(errorOutput)
        isIgnoreExitValue = true
    }
    if (result.exitValue == 0) {
        it.toString().trim().substring(0, 8)
    } else {
        "nogit"
    }
}

tasks.withType<Jar> {
    manifest {
        val buildNumber = System.getenv("BUILD_NUMBER")
        val linkHumanVersion: String =
            if (project.version.toString().endsWith("-SNAPSHOT")) {
                if (buildNumber == null) {
                    "${project.version} (git-$currentShortRevision)"
                } else {
                    "${project.version} (git-$currentShortRevision-b$buildNumber)"
                }
            } else {
                archiveVersion.get()
            }
        attributes["Implementation-Version"] = linkHumanVersion
    }
}

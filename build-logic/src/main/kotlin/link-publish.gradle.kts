plugins {
    java
    `maven-publish`
}

extensions.configure<PublishingExtension> {
    repositories {
        maven {
            credentials(PasswordCredentials::class.java)

            name = if (version.toString().endsWith("SNAPSHOT")) "paperSnapshots" else "paper" // "paper" is seemingly not defined
            val base = "https://artifactory.papermc.io/artifactory"
            val releasesRepoUrl = "$base/releases/"
            val snapshotsRepoUrl = "$base/snapshots/"
            setUrl(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Link")
                description.set("The modern, next-generation Minecraft server proxy")
                url.set("https://papermc.io/software/link")
                scm {
                    url.set("https://github.com/PaperMC/Link")
                    connection.set("scm:git:https://github.com/PaperMC/Link.git")
                    developerConnection.set("scm:git:https://github.com/PaperMC/Link.git")
                }
            }
        }
    }
}

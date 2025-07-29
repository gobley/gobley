/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.build

import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

abstract class GobleyGradleBuildExtension(private val project: Project) {
    val bindgenInfo = BindgenInfo.fromCargoManifest(
        project.rootProject.layout.projectDirectory.file("../crates/gobley-uniffi-bindgen/Cargo.toml").asFile
    )

    fun configureGobleyGradleProject(
        description: String,
        gradlePlugin: Boolean = false,
        signing: Boolean = needsSigning(),
    ) {
        project.configureGobleyGradleProject(description, gradlePlugin, signing)
    }

    private fun needsSigning(): Boolean {
        return project.findProperty("signingInMemoryKey") != null
    }
}

private fun Project.configureGobleyGradleProject(
    description: String,
    gradlePlugin: Boolean,
    signing: Boolean,
) {
    configureProjectProperties(description)
    configureKotlinProperties()
    tasks.withType<Test> {
        useJUnitPlatform()
        reports {
            junitXml.required.set(true)
        }
    }
    configureMavenCentralPublishing(gradlePlugin, signing)
}

private fun Project.configureProjectProperties(
    description: String
) {
    val bindgenInfo = BindgenInfo.fromCargoManifest(
        rootProject.layout.projectDirectory.file("../crates/gobley-uniffi-bindgen/Cargo.toml").asFile
    )
    group = "dev.gobley.gradle"
    version = when {
        bindgenInfo.version.contains('-') -> bindgenInfo.version.substringBefore('-') + "-SNAPSHOT"
        else -> bindgenInfo.version
    }
    this.description = description
}

private fun Project.configureKotlinProperties() {
    extensions.configure(KotlinJvmProjectExtension::class.java) {
        jvmToolchain(17)
    }
}

private fun Project.configureMavenCentralPublishing(gradlePlugin: Boolean, signing: Boolean) {
    extensions.configure(MavenPublishBaseExtension::class.java) {
        configure(
            if (gradlePlugin) {
                GradlePlugin(
                    javadocJar = JavadocJar.Javadoc(),
                    sourcesJar = true,
                )
            } else {
                KotlinJvm(
                    javadocJar = JavadocJar.Empty(),
                    sourcesJar = true,
                )
            }
        )
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        if (signing) {
            signAllPublications()
        }
        coordinates(group.toString(), name, version.toString())
        pom {
            name.set(project.name)
            description.set(project.description)
            inceptionYear.set("2023")
            url.set(propertyOrEnv("gobley.projects.gradle.pom.url"))
            licenses {
                license {
                    name.set(propertyOrEnv("gobley.projects.gradle.pom.license.name"))
                    url.set(propertyOrEnv("gobley.projects.gradle.pom.license.url"))
                }
            }
            developers {
                for (developerIdx in generateSequence(0) { it + 1 }) {
                    val propertyNamePrefix = "gobley.projects.gradle.pom.developer$developerIdx"
                    val developerId = propertyOrEnv("$propertyNamePrefix.id") ?: break
                    val developerName = propertyOrEnv("$propertyNamePrefix.name") ?: break
                    developer {
                        id.set(developerId)
                        name.set(developerName)
                    }
                }
            }
            scm {
                url.set(propertyOrEnv("gobley.projects.gradle.pom.scm.url"))
                connection.set(propertyOrEnv("gobley.projects.gradle.pom.scm.connection"))
                developerConnection.set(propertyOrEnv("gobley.projects.gradle.pom.scm.developerConnection"))
            }
        }
    }
}

private fun Project.propertyOrEnv(propertyName: String, envName: String = propertyName): String? {
    return findProperty(propertyName)?.toString()
        ?: rootProject.findProperty(propertyName)?.toString()
        ?: System.getenv(envName)
}

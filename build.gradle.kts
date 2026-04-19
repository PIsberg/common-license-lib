import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.CoreJavadocOptions

plugins {
    `java-library`
    jacoco
    id("com.vanniktech.maven.publish") version "0.30.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

val junitVersion = "5.11.3"

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
}

tasks.test {
    useJUnitPlatform()
    forkEvery = 1
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)

    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(
        groupId = project.group.toString(),
        artifactId = "common-license-lib",
        version = project.version.toString()
    )

    pom {
        name = "Common License Lib"
        description = "Drop-in JVM license gate for PolyForm Commercial-style distribution. " +
                "Validates against Keygen.sh, integrates with LemonSqueezy for checkout, " +
                "and lets common private-email users through without a key."
        url = "https://github.com/PIsberg/common-license-lib"

        licenses {
            license {
                name = "PolyForm Free Trial License 1.0.0"
                url = "https://polyformproject.org/licenses/free-trial/1.0.0/"
                distribution = "repo"
            }
        }

        developers {
            developer {
                id = "PIsberg"
                name = "Peter Isberg"
                url = "https://github.com/PIsberg"
            }
        }

        scm {
            url = "https://github.com/PIsberg/common-license-lib"
            connection = "scm:git:https://github.com/PIsberg/common-license-lib.git"
            developerConnection = "scm:git:https://github.com/PIsberg/common-license-lib.git"
        }

        issueManagement {
            system = "GitHub"
            url = "https://github.com/PIsberg/common-license-lib/issues"
        }
    }
}

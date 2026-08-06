import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.CoreJavadocOptions

plugins {
    `java-library`
    jacoco
    id("com.vanniktech.maven.publish") version "0.37.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

val junitVersion = "6.1.2"
val vibetagsVersion = "1.0.1"

dependencies {
    // Compile-time only. VibeTags annotations are RetentionPolicy.SOURCE, so nothing
    // reaches the jar and the library keeps its zero-runtime-dependency guarantee.
    compileOnly("se.deversity.vibetags:vibetags-processor:$vibetagsVersion")
    annotationProcessor("se.deversity.vibetags:vibetags-processor:$vibetagsVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitVersion")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(
        listOf(
            // Without an explicit root the processor resolves it from the JVM working
            // directory, which under Gradle is the daemon's directory, not the project.
            "-Avibetags.root=${rootDir}",
            // H1 used by the generated llms.txt / llms-full.txt, if opted in.
            "-Avibetags.project=common-license-lib",
        )
    )
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
    // The SonatypeHost argument is gone in 0.37.0 — the Central Portal is the only host.
    publishToMavenCentral(automaticRelease = true)

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
                name = "PolyForm Noncommercial License 1.0.0"
                url = "https://polyformproject.org/licenses/noncommercial/1.0.0/"
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

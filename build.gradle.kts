@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    idea
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kvision)
    alias(libs.plugins.versions)
//    alias(libs.plugins.config)
    alias(libs.plugins.kotlinter)
//    alias(libs.plugins.kotest)
//    alias(libs.plugins.diktat)
//    alias(libs.plugins.detekt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kiluaRpc)
}

version = "1.0.0"
group = "com.github.pambrose"

repositories {
    mavenCentral()
    // history-walk-slides is only published on JitPack
    maven(url = "https://jitpack.io") {
        content { includeGroup("com.github.pambrose") }
    }
    // mavenLocal()
}

//buildConfig {
//  packageName("com.github.pambrose")
//  buildConfigField("String", "CORE_NAME", "\"${project.name}\"")
//  buildConfigField("String", "CORE_VERSION", provider { "\"${project.version}\"" })
//  buildConfigField("String", "CORE_RELEASE_DATE", "\"9/17/21\"")
//  buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
//}

val mainClassName = "io.ktor.server.cio.EngineMain"

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
    jvm {
//        withJava()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
        }

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass = mainClassName
        }
    }

    js {
        browser {
            commonWebpackConfig(Action {
                outputFileName = "main.bundle.js"
                sourceMaps = false
            })
            testTask(Action {
                useKarma {
                    useChromeHeadless()
                }
            })
        }
        binaries.executable()
        compilerOptions {
            target.set("es2015")
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlin.uuid.ExperimentalUuidApi")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.DelicateCoroutinesApi")
                optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
            }
        }

        commonMain.dependencies {
            api(libs.kilua.rpc.ktor)
            implementation(libs.kvision.common.remote)
        }

        commonTest.dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
        }

        jvmMain.dependencies {
            implementation(project.dependencies.platform(libs.kotlin.bom))
            implementation(libs.kotlin.reflect)

            implementation(project.dependencies.platform(libs.ktor.bom))
//            implementation(libs.bundles.ktor.client)
            implementation(libs.ktor.server.core)

            implementation(project.dependencies.platform(libs.utils.bom))
            implementation(libs.common.utils.core)

            implementation(project.dependencies.platform(libs.exposed.bom))
            implementation(libs.exposed.core)

//            implementation(libs.dropwizard.metrics)

            implementation(libs.kotlin.logging)

            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.html.builder)
            implementation(libs.ktor.server.sessions)
            implementation(libs.ktor.server.auth)
            implementation(libs.ktor.server.metrics)
            implementation(libs.ktor.server.metrics.micrometer)
            implementation(libs.ktor.server.compression)
            implementation(libs.ktor.server.call.logging)
            implementation(libs.ktor.server.default.headers)
            implementation(libs.ktor.server.status.pages)

            implementation(libs.common.utils.ktor)
            implementation(libs.common.utils.email)
            implementation(libs.common.utils.exposed)

            implementation(libs.history.walk.slides)

            //implementation("dev.hayden:khealth:$khealthVersion")

            implementation(libs.postgresql)
            implementation(libs.pgjdbc.ng)
            implementation(libs.hikariCP)
            implementation(libs.exposed.jdbc)
            implementation(libs.exposed.kotlin.datetime)
            implementation(libs.flyway.core)
            runtimeOnly(libs.flyway.database.postgresql)

            //implementation("commons-codec:commons-codec:$commonsCodecVersion")
            //implementation("com.axiomalaska:jdbc-named-parameters:$jdbcNamedParametersVersion")
            //implementation("com.github.andrewoma.kwery:core:$kweryVersion")

            implementation(libs.flexmark)

            implementation(libs.common.utils.script.common)
            implementation(libs.common.utils.script.kotlin)
            runtimeOnly(libs.kotlin.scripting.jsr223)

            implementation(libs.logback.classic)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }

        jsMain.dependencies {
            implementation(libs.kvision.core)
            implementation(libs.kvision.bootstrap)
            implementation(libs.kvision.state)
            implementation(libs.kvision.fontawesome)
            implementation(libs.kvision.rest)
        }

        jsTest.dependencies {
            implementation(kotlin("test-js"))
            implementation(libs.kvision.testutils)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.withType<LintTask> {
    // Exclude all generated files from lint checks, including KSP-generated files
    source = source.minus(fileTree("build/generated")).asFileTree
}

kotlinter {
    reporters = arrayOf("checkstyle", "plain")
}

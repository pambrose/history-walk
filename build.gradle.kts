import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    val kotlinVersion: String by System.getProperties()
    val kvisionVersion: String by System.getProperties()
    val versionsVersion: String by System.getProperties()
    val configVersion: String by System.getProperties()
    val kotlinterVersion: String by System.getProperties()

//  `maven-publish`

    kotlin("plugin.serialization") version kotlinVersion
    kotlin("multiplatform") version kotlinVersion
    // This is required by BuildConfig
    // id("idea")
    id("io.kvision") version kvisionVersion
    id("org.jmailen.kotlinter") version kotlinterVersion
    id("com.github.ben-manes.versions") version versionsVersion
    // id("com.github.gmazzo.buildconfig") version configVersion
}

version = "1.0.0"
group = "com.github.pambrose"

repositories {
    google()
    mavenCentral()
    maven(url = "https://jitpack.io")
    // mavenLocal()
}

//buildConfig {
//  packageName("com.github.pambrose")
//  buildConfigField("String", "CORE_NAME", "\"${project.name}\"")
//  buildConfigField("String", "CORE_VERSION", provider { "\"${project.version}\"" })
//  buildConfigField("String", "CORE_RELEASE_DATE", "\"9/17/21\"")
//  buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
//}

// Versions
val kotlinVersion: String by System.getProperties()
val kvisionVersion: String by System.getProperties()
val kiluaVersion: String by System.getProperties()
val commonsCodecVersion: String by project
val exposedVersion: String by project
val flexmarkVersion: String by project
val h2Version: String by project
val hikariVersion: String by project
//val khealthVersion: String by project
val kweryVersion: String by project
val logbackVersion: String by project
val loggingVersion: String by project
val pgjdbcVersion: String by project
val pgsqlVersion: String by project
val jdbcNamedParametersVersion: String by project
val ktorVersion: String by project
val slidesVersion: String by project
val utilsVersion: String by project

val webDir = file("src/jsMain/web")
val mainClassName = "io.ktor.server.cio.EngineMain"

kotlin {
    jvmToolchain(17)
    jvm {
//        withJava()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            freeCompilerArgs.add("-Xcontext-parameters")
        }

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass = mainClassName
        }
    }

    js(IR) {
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
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.DelicateCoroutinesApi")
            }
        }

        val commonMain by getting {
            dependencies {
                api("dev.kilua:kilua-rpc-ktor:${kiluaVersion}")
                implementation("io.kvision:kvision-common-remote:${kvisionVersion}")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

                implementation("io.ktor:ktor-server-core:$ktorVersion")
                implementation("io.ktor:ktor-server-cio:$ktorVersion")
                implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
                implementation("io.ktor:ktor-server-sessions:$ktorVersion")
                implementation("io.ktor:ktor-server-auth:$ktorVersion")
                implementation("io.ktor:ktor-server-metrics:$ktorVersion")
                implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
                implementation("io.ktor:ktor-server-compression:$ktorVersion")
                implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
                implementation("io.ktor:ktor-server-default-headers:$ktorVersion")
                implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

                implementation("com.github.pambrose.common-utils:ktor-server-utils:$utilsVersion")
                implementation("com.github.pambrose.common-utils:exposed-utils:$utilsVersion")

                implementation("com.github.pambrose:history-walk-slides:$slidesVersion")

                //implementation("dev.hayden:khealth:$khealthVersion")

                implementation("org.postgresql:postgresql:$pgsqlVersion")
                implementation("com.impossibl.pgjdbc-ng:pgjdbc-ng-all:$pgjdbcVersion")
                implementation("com.zaxxer:HikariCP:$hikariVersion")
                implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")

                //implementation("commons-codec:commons-codec:$commonsCodecVersion")
                //implementation("com.axiomalaska:jdbc-named-parameters:$jdbcNamedParametersVersion")
                //implementation("com.github.andrewoma.kwery:core:$kweryVersion")

                implementation("com.vladsch.flexmark:flexmark:$flexmarkVersion")

                implementation("com.github.pambrose.common-utils:core-utils:$utilsVersion")
                implementation("com.github.pambrose.common-utils:script-utils-common:$utilsVersion")
                implementation("com.github.pambrose.common-utils:script-utils-kotlin:$utilsVersion")
                runtimeOnly("org.jetbrains.kotlin:kotlin-scripting-jsr223:$kotlinVersion")

                implementation("io.github.oshai:kotlin-logging-jvm:$loggingVersion")
                implementation("ch.qos.logback:logback-classic:$logbackVersion")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jsMain by getting {
            dependencies {
                implementation("io.kvision:kvision:$kvisionVersion")
                implementation("io.kvision:kvision-bootstrap:$kvisionVersion")
                implementation("io.kvision:kvision-state:$kvisionVersion")
                implementation("io.kvision:kvision-fontawesome:$kvisionVersion")
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
                implementation("io.kvision:kvision-testutils:$kvisionVersion")
            }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.withType<LintTask> {
    // Exclude all generated files from lint checks, including KSP-generated files
    source = source.minus(fileTree("build/generated")).asFileTree
    source = source.minus(fileTree("build/generated/ksp")).asFileTree
}

kotlinter {
    reporters = arrayOf("checkstyle", "plain")
}

// This will allow us to grab recent -SNAPSHOT versions from jitpack.io
configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

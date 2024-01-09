import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
  val kotlinVersion: String by System.getProperties()
  val kvisionVersion: String by System.getProperties()
  val versionsVersion: String by System.getProperties()
  val configVersion: String by System.getProperties()
  val flywayVersion: String by System.getProperties()
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
  id("org.flywaydb.flyway") version flywayVersion
}

version = "1.0.0"
group = "com.github.pambrose"

repositories {
  google()
  mavenCentral()
    maven(url = "https://jitpack.io")
    mavenLocal()
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
        withJava()
    compilations.all {
      kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
      }
    }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass.set(mainClassName)
        }
  }

    js(IR) {
    browser {
        commonWebpackConfig(Action {
        outputFileName = "main.bundle.js"
        })
      runTask {
//        outputFileName = "main.bundle.js"
        sourceMaps = false
          devServer = DevServer(
          open = false,
          port = 3000,
          proxy = mutableMapOf(
            "/login" to "http://localhost:8080",
            "/logout" to "http://localhost:8080",
            "/contentreset" to "http://localhost:8080",
            "/userreset" to "http://localhost:8080",
            "/summary" to "http://localhost:8080",
            "/reasons" to "http://localhost:8080",
            "/slides" to "http://localhost:8080",
            "/slide/*" to "http://localhost:8080",
            "/kv/*" to "http://localhost:8080",
            "/kvws/*" to mapOf("target" to "ws://localhost:8080", "ws" to true)
          ),
              //static = mutableListOf("$buildDir/processedResources/frontend/main")
          static = mutableListOf("${layout.buildDirectory.asFile.get()}/processedResources/js/main")
        )
      }
//      webpackTask {
//        outputFileName = "main.bundle.js"
//      }
        testTask(Action {
        useKarma {
          useChromeHeadless()
        }
        })
    }
    binaries.executable()
  }

  sourceSets {
      all {
          tasks.withType<LintTask> {
              this.source = this.source.minus(fileTree("build")).asFileTree
          }

          tasks.withType<FormatTask> {
              this.source = this.source.minus(fileTree("build")).asFileTree
          }

          languageSettings {
              optIn("kotlin.time.ExperimentalTime")
              optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
              optIn("kotlinx.coroutines.DelicateCoroutinesApi")
          }
      }

    val commonMain by getting {
      dependencies {
        api("io.kvision:kvision-server-ktor:$kvisionVersion")
      }
      // kotlin.srcDir("build/generated-src/common")
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
      }
    }

      val jvmMain by getting {
      dependencies {
          // implementation(kotlin("stdlib-jdk8"))
        implementation(kotlin("reflect"))

        implementation("io.ktor:ktor-server-core:$ktorVersion")
        implementation("io.ktor:ktor-server-cio:$ktorVersion")
        implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
        implementation("io.ktor:ktor-server-sessions:$ktorVersion")
        implementation("io.ktor:ktor-server-auth:$ktorVersion")
        implementation("io.ktor:ktor-server-metrics:$ktorVersion")
          implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
        implementation("io.ktor:ktor-server-compression:$ktorVersion")
          implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
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

        implementation("io.github.microutils:kotlin-logging:$loggingVersion")
        implementation("ch.qos.logback:logback-classic:$logbackVersion")
      }
    }

      val jvmTest by getting {
      dependencies {
        implementation(kotlin("test"))
          //  implementation(kotlin("test-junit"))
      }
    }

      val jsMain by getting {
//      resources.srcDir(webDir)
      dependencies {
        implementation("io.kvision:kvision:$kvisionVersion")
        implementation("io.kvision:kvision-bootstrap:$kvisionVersion")
        implementation("io.kvision:kvision-state:$kvisionVersion")
        implementation("io.kvision:kvision-fontawesome:$kvisionVersion")
      }
//      kotlin.srcDir("build/generated-src/frontend")
    }

      val jsTest by getting {
      dependencies {
        implementation(kotlin("test-js"))
        implementation("io.kvision:kvision-testutils:$kvisionVersion")
      }
    }

    all {
      languageSettings.optIn("kotlin.time.ExperimentalTime")
    }
  }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    kotlinter {
        ignoreFailures = false
        reporters = arrayOf("checkstyle", "plain")
    }
}

//afterEvaluate {
//  tasks {
//    create("frontendArchive", Jar::class).apply {
//      dependsOn("frontendBrowserProductionWebpack")
//      group = "package"
//      archiveAppendix.set("frontend")
//      val distribution =
//        project.tasks.getByName("frontendBrowserProductionWebpack", KotlinWebpack::class).destinationDirectory
//      from(distribution) {
//        include("*.*")
//      }
//      from(webDir)
//      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//      into("/assets")
//      inputs.files(distribution, webDir)
//      outputs.file(archiveFile)
//      manifest {
//        attributes(
//          mapOf(
//            "Implementation-Title" to rootProject.name,
//            "Implementation-Group" to rootProject.group,
//            "Implementation-Version" to rootProject.version,
//            "Timestamp" to System.currentTimeMillis()
//          )
//        )
//      }
//    }
//    getByName("backendProcessResources", Copy::class) {
//      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//    }
//    getByName("backendJar").group = "package"
//    create("jar", Jar::class).apply {
//      dependsOn("frontendArchive", "backendJar")
//      group = "package"
//      manifest {
//        attributes(
//          mapOf(
//            "Implementation-Title" to rootProject.name,
//            "Implementation-Group" to rootProject.group,
//            "Implementation-Version" to rootProject.version,
//            "Timestamp" to System.currentTimeMillis(),
//            "Main-Class" to mainClassName
//          )
//        )
//      }
//      val dependencies = configurations["backendRuntimeClasspath"].filter { it.name.endsWith(".jar") } +
//          project.tasks["backendJar"].outputs.files +
//          project.tasks["frontendArchive"].outputs.files
//      dependencies.forEach {
//        if (it.isDirectory) from(it) else from(zipTree(it))
//      }
//      exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
//      inputs.files(dependencies)
//      outputs.file(archiveFile)
//      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//    }
//    create("backendRun", JavaExec::class) {
//      dependsOn("compileKotlinBackend")
//      group = "run"
//      mainClass.set(mainClassName)
//      classpath =
//        configurations["backendRuntimeClasspath"] + project.tasks["compileKotlinBackend"].outputs.files +
//            project.tasks["backendProcessResources"].outputs.files
//      workingDir = buildDir
//    }
//  }
//}

// This is for flyway
buildscript {
  dependencies {
    classpath("org.postgresql:postgresql:42.5.1")
  }
}

tasks.findByName("lintKotlinCommonMain")?.apply {
    dependsOn("kspCommonMainKotlinMetadata")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}

//flyway {
//  driver = "org.postgresql.Driver"
//  url = "jdbc:postgresql://localhost:5432/historywalk"
//  user = "postgres"
//  password = "docker"
//  locations = arrayOf("filesystem:src/backendMain/resources/db/migration")
//}

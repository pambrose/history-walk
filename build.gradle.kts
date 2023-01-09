import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
  val kotlinVersion: String by System.getProperties()
  val kvisionVersion: String by System.getProperties()
  val versionsVersion: String by System.getProperties()
  val configVersion: String by System.getProperties()
  val flywayVersion: String by System.getProperties()

//  `maven-publish`

  kotlin("multiplatform") version kotlinVersion
  kotlin("plugin.serialization") version kotlinVersion
  // This is required by BuildConfig
  // id("idea")
  id("io.kvision") version kvisionVersion
  id("com.github.ben-manes.versions") version versionsVersion
  // id("com.github.gmazzo.buildconfig") version configVersion
  id("org.flywaydb.flyway") version flywayVersion
}

version = "1.0.0"
group = "com.github.pambrose"

repositories {
  google()
  mavenCentral()
  maven { url = uri("https://jitpack.io") }
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

val webDir = file("src/frontendMain/web")
val mainClassName = "io.ktor.server.cio.EngineMain"


kotlin {
  jvm("backend") {
    compilations.all {
      java {
        targetCompatibility = JavaVersion.VERSION_17
      }
      kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjsr305=strict")
      }
    }
  }

  js("frontend") {
    browser {
      commonWebpackConfig {
        outputFileName = "main.bundle.js"
      }
      runTask {
//        outputFileName = "main.bundle.js"
        sourceMaps = false
        devServer = KotlinWebpackConfig.DevServer(
          open = false,
          port = 3000,
          proxy = mutableMapOf(
            "/kv/*" to "http://localhost:8080",
            "/login" to "http://localhost:8080",
            "/logout" to "http://localhost:8080",
            "/contentreset" to "http://localhost:8080",
            "/userreset" to "http://localhost:8080",
            "/summary" to "http://localhost:8080",
            "/reasons" to "http://localhost:8080",
            "/slides" to "http://localhost:8080",
            "/slide/*" to "http://localhost:8080",
            "/kvws/*" to mapOf("target" to "ws://localhost:8080", "ws" to true)
          ),
          static = mutableListOf("$buildDir/processedResources/frontend/main")
        )
      }
//      webpackTask {
//        outputFileName = "main.bundle.js"
//      }
      testTask {
        useKarma {
          useChromeHeadless()
        }
      }
    }
    binaries.executable()
  }

  sourceSets {
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

    val backendMain by getting {
      dependencies {
        implementation(kotlin("stdlib-jdk8"))
        implementation(kotlin("reflect"))

        implementation("io.ktor:ktor-server-core:$ktorVersion")
        implementation("io.ktor:ktor-server-cio:$ktorVersion")
        implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
        implementation("io.ktor:ktor-server-sessions:$ktorVersion")
        implementation("io.ktor:ktor-server-auth:$ktorVersion")
        implementation("io.ktor:ktor-server-metrics:$ktorVersion")
        implementation("io.ktor:ktor-server-compression:$ktorVersion")
        implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
        implementation("io.ktor:ktor-server-default-headers:$ktorVersion")
        implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

        implementation("com.github.pambrose.common-utils:ktor-server-utils:$utilsVersion")
        implementation("com.github.pambrose:history-walk-slides:$slidesVersion")

        //implementation("dev.hayden:khealth:$khealthVersion")

        implementation("org.postgresql:postgresql:$pgsqlVersion")
        implementation("com.impossibl.pgjdbc-ng:pgjdbc-ng-all:$pgjdbcVersion")
        implementation("com.zaxxer:HikariCP:$hikariVersion")
        implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
        implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
        implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
        implementation("com.github.pambrose.common-utils:exposed-utils:$utilsVersion")

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

    val backendTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(kotlin("test-junit"))
      }
    }

    val frontendMain by getting {
      resources.srcDir(webDir)
      dependencies {
        implementation("io.kvision:kvision:$kvisionVersion")
        implementation("io.kvision:kvision-bootstrap:$kvisionVersion")
        implementation("io.kvision:kvision-state:$kvisionVersion")
        implementation("io.kvision:kvision-fontawesome:$kvisionVersion")
        implementation("io.kvision:kvision-i18n:$kvisionVersion")
      }
//      kotlin.srcDir("build/generated-src/frontend")
    }

    val frontendTest by getting {
      dependencies {
        implementation(kotlin("test-js"))
        implementation("io.kvision:kvision-testutils:$kvisionVersion")
      }
    }

    all {
      languageSettings.optIn("kotlin.time.ExperimentalTime")
    }
  }
}

afterEvaluate {
  tasks {
    create("frontendArchive", Jar::class).apply {
      dependsOn("frontendBrowserProductionWebpack")
      group = "package"
      archiveAppendix.set("frontend")
      val distribution =
        project.tasks.getByName("frontendBrowserProductionWebpack", KotlinWebpack::class).destinationDirectory
      from(distribution) {
        include("*.*")
      }
      from(webDir)
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      into("/assets")
      inputs.files(distribution, webDir)
      outputs.file(archiveFile)
      manifest {
        attributes(
          mapOf(
            "Implementation-Title" to rootProject.name,
            "Implementation-Group" to rootProject.group,
            "Implementation-Version" to rootProject.version,
            "Timestamp" to System.currentTimeMillis()
          )
        )
      }
    }
    getByName("backendProcessResources", Copy::class) {
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    getByName("backendJar").group = "package"
    create("jar", Jar::class).apply {
      dependsOn("frontendArchive", "backendJar")
      group = "package"
      manifest {
        attributes(
          mapOf(
            "Implementation-Title" to rootProject.name,
            "Implementation-Group" to rootProject.group,
            "Implementation-Version" to rootProject.version,
            "Timestamp" to System.currentTimeMillis(),
            "Main-Class" to mainClassName
          )
        )
      }
      val dependencies = configurations["backendRuntimeClasspath"].filter { it.name.endsWith(".jar") } +
          project.tasks["backendJar"].outputs.files +
          project.tasks["frontendArchive"].outputs.files
      dependencies.forEach {
        if (it.isDirectory) from(it) else from(zipTree(it))
      }
      exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
      inputs.files(dependencies)
      outputs.file(archiveFile)
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    create("backendRun", JavaExec::class) {
      dependsOn("compileKotlinBackend")
      group = "run"
      main = mainClassName
      classpath =
        configurations["backendRuntimeClasspath"] + project.tasks["compileKotlinBackend"].outputs.files +
            project.tasks["backendProcessResources"].outputs.files
      workingDir = buildDir
    }
  }
}

// This is for flyway
buildscript {
  dependencies {
    classpath("org.postgresql:postgresql:42.5.1")
  }
}

flyway {
  driver = "org.postgresql.Driver"
  url = "jdbc:postgresql://localhost:5432/historywalk"
  user = "postgres"
  password = "docker"
  locations = arrayOf("filesystem:src/backendMain/resources/db/migration")
}

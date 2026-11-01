package com.pambrose.historywalk.content

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import java.io.File

// A student's progress is stored by path name, the chain of slide titles from the root (users.last_path_name,
// user_choices.from_path_name and to_path_name). A title edit or a moved slide changes the path names at and below it,
// and a student saved there silently resumes at the root slide. This spec pins every path name a student can reach in
// each deck to a snapshot, src/jvmTest/resources/slide-paths/<deck>.txt (one sorted path per line), so such a change
// fails CI and has to be made deliberately. Run with -PupdateSlidePaths=true, the spec rewrites the snapshots instead.
class SlidePathsTest : StringSpec() {
  init {
    deckNames.forEach { name ->
      "$name: the path names a student can reach match the snapshot slide-paths/$name.txt" {
        val live = slideDeckFor(name).reachableSlides().map { it.pathName }.sorted()
        if (updating) writeSnapshot(name, live) else checkSnapshot(name, live)
      }
    }
  }

  companion object {
    // The SLIDES_VARIABLE_NAME values, which also name the snapshot files
    private val deckNames = listOf("jermain", "moses")

    // build.gradle.kts passes -PupdateSlidePaths=true on to the test JVM as this system property
    private const val UPDATE_PROPERTY = "updateSlidePaths"
    private const val SNAPSHOT_DIR = "src/jvmTest/resources/slide-paths"
    private const val UPDATE_COMMAND =
      "./gradlew jvmTest --tests 'com.pambrose.historywalk.content.SlidePathsTest' -P$UPDATE_PROPERTY=true"

    private val updating = System.getProperty(UPDATE_PROPERTY).toBoolean()

    private fun snapshotPath(name: String) = "$SNAPSHOT_DIR/$name.txt"

    private fun checkSnapshot(
      name: String,
      live: List<String>,
    ) {
      val expected =
        SlidePathsTest::class.java
          .getResource("/slide-paths/$name.txt")
          ?.readText()
          ?.lines()
          ?.filter { it.isNotEmpty() }
          ?: fail("There is no snapshot ${snapshotPath(name)}. Write it with:\n  $UPDATE_COMMAND")
      if (live != expected) fail(mismatch(name, expected, live))
    }

    private fun mismatch(
      name: String,
      expected: List<String>,
      live: List<String>,
    ) = buildString {
      val disappeared = expected - live.toSet()
      val appeared = live - expected.toSet()

      appendLine("The $name deck's path names differ from the snapshot ${snapshotPath(name)}.")
      if (disappeared.isEmpty() && appeared.isEmpty())
        appendLine("The paths are the same, but the file is not one sorted path per line.")
      if (disappeared.isNotEmpty()) {
        appendLine()
        appendLine(
          "${disappeared.size} path(s) disappeared. A student saved at one of these resumes at the root slide, and " +
            "their choices that name it no longer match a slide:",
        )
        disappeared.forEach { appendLine("  - $it") }
      }
      if (appeared.isNotEmpty()) {
        appendLine()
        appendLine("${appeared.size} path(s) appeared:")
        appeared.forEach { appendLine("  + $it") }
      }
      appendLine()
      if (disappeared.isEmpty()) {
        appendLine("No saved progress is lost. Update the snapshot, and commit it with the deck change:")
      } else {
        appendLine(
          "Progress is stored by path name, so a renamed or moved slide orphans it. Make the change deliberately: " +
            "either move the stored progress to the new path names (a Flyway migration that rewrites " +
            "users.last_path_name and user_choices.from_path_name and to_path_name), or accept that the affected " +
            "students restart. Then update the snapshot, and commit it with the deck change:",
        )
      }
      append("  $UPDATE_COMMAND")
    }

    // The Gradle test task runs in the project directory, so the source copy is rewritten, not the one on the classpath
    private fun writeSnapshot(
      name: String,
      live: List<String>,
    ) {
      val file = File(snapshotPath(name))
      check(File("src/jvmTest").isDirectory) {
        "$UPDATE_PROPERTY writes ${file.path} relative to the working directory, ${File("").absolutePath}, " +
          "which is not the project directory"
      }
      file.parentFile.mkdirs()
      file.writeText(live.joinToString("\n", postfix = "\n"))
    }
  }
}

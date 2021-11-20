package com.github.pambrose.slides

import com.github.pambrose.Content
import com.github.pambrose.ScriptPools.kotlinScriptPool
import com.github.pambrose.dbms.UsersTable
import mu.KLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class SlideContent {
  val allSlides = mutableMapOf<String, Slide>()
  private lateinit var rootSlide: Slide

  fun slide(title: String, block: Slide.() -> Unit = { }) {
    Slide(title, this).block()
  }

  fun findSlide(uuid: String) =
    transaction {
      (UsersTable
        .slice(UsersTable.lastTitle)
        .select { UsersTable.uuidCol eq UUID.fromString(uuid) }
        .map { it[UsersTable.lastTitle] }
        .firstOrNull() ?: error("Missing uuid: $uuid"))
        .let { title ->
          (if (title == Content.ROOT) rootSlide else allSlides[title]) ?: error("Invalid title: $title")
        }
    }

  fun verifySlides() {
    allSlides.forEach { (title, slide) ->
      slide.choices.forEach { (_, dest) ->
        val destSlide = allSlides[dest] ?: error("Missing slide with title: $dest")
        if (destSlide.parentSlide != null)
          error("""Parent slide already assigned to: "$dest"""")
        destSlide.parentSlide = slide
      }

      if (slide.success && slide.hasChoices)
        error("""Slide "$title" has both success and choices""")
    }

    allSlides.filter { it.value.success }.count()
      .also { successCount ->
        if (successCount == 0)
          error("No success slide found")

        if (successCount > 1)
          error("Multiple success slides found")
      }

    rootSlide =
      allSlides.values.filter { it.parentSlide == null }.let { nullParents ->
        when {
          nullParents.size > 1 -> error("Multiple top-level slides: ${nullParents.map { it.title }}")
          nullParents.isEmpty() -> error("Missing a top-level slide")
          else -> nullParents.first()
        }
      }
  }

  companion object : KLogging() {
    private suspend fun evalDsl(code: String, sourceName: String) =
      try {
        kotlinScriptPool.eval { eval(code) as SlideContent }//.apply { validate() }
      } catch (e: Throwable) {
        logger.info { "Error in $sourceName:\n$code" }
        throw e
      }
  }
}

fun slideContent(block: SlideContent.() -> Unit) =
  SlideContent().apply(block).apply { }

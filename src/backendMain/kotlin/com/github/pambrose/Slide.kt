package com.github.pambrose

import com.github.pambrose.Content.ROOT
import com.github.pambrose.dbms.UsersTable
import mu.KLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class Slide(val title: String) {
  var parentSlide: Slide? = null
  var content: String = ""
  var success = false
  val choices = mutableMapOf<String, String>()
  var choiceOrientation: ChoiceOrientation = ChoiceOrientation.VERTICAL

  val hasChoices: Boolean
    get() = choices.isNotEmpty()

  init {
    require(title !in allSlides.keys) { "Slide titles must be unique: $title" }
    allSlides[title] = this
  }

  fun choice(choice: String, destination: String) {
    choices[choice] = destination
  }

  companion object : KLogging() {
    val allSlides = mutableMapOf<String, Slide>()
    private lateinit var rootSlide: Slide

    fun slide(title: String, block: Slide.() -> Unit = { }) {
      Slide(title).block()
    }

    fun findSlide(uuid: String) =
      transaction {
        (UsersTable
          .slice(UsersTable.lastTitle)
          .select { UsersTable.uuidCol eq UUID.fromString(uuid) }
          .map { it[UsersTable.lastTitle] }
          .firstOrNull() ?: error("Missing uuid: $uuid"))
          .let { title ->
            (if (title == ROOT) rootSlide else allSlides[title]) ?: error("Invalid title: $title")
          }
      }

    fun verifySlides() {
      allSlides.forEach { (title, slide) ->
        slide.choices.forEach { (_, dest) ->
          val destSlide = allSlides[dest] ?: throw IllegalArgumentException("Missing slide with title: $dest")
          if (destSlide.parentSlide != null)
            throw IllegalArgumentException("Parent slide already assigned to : $dest")
          destSlide.parentSlide = slide
        }

        if (slide.success && slide.hasChoices)
          throw IllegalArgumentException("""Slide "$title" has both success and choices""")
      }

      allSlides.filter { it.value.success }.count()
        .also { successCount ->
          if (successCount == 0)
            throw IllegalArgumentException("No success slides found")

          if (successCount > 1)
            throw IllegalArgumentException("Multiple success slides found")
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
  }
}
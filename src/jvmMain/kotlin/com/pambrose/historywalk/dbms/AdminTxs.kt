package com.pambrose.historywalk.dbms

import com.pambrose.historywalk.slides.SlideDeck.Companion.ROOT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime

/**
 * What the admin page reads and changes: the switch that has every teacher walk as a student does, and the clear that
 * deletes the walks teachers made. Each runs in the caller's transaction, if there is one.
 */
object AdminTxs {
  private val logger = KotlinLogging.logger {}

  /** Whether teachers see the student view: their pages are a student's, and their moves count as a student's. */
  fun teachersSeeStudentView(): Boolean =
    transaction {
      with(SettingsTable) {
        select(teachersSeeStudentView)
          .where { id eq ROW }
          .map { it[teachersSeeStudentView] }
          .single()
      }
    }

  fun setTeachersSeeStudentView(studentView: Boolean) {
    transaction {
      with(SettingsTable) {
        update({ id eq ROW }) { row ->
          row[teachersSeeStudentView] = studentView
          row[updated] = LocalDateTime.now().toKotlinLocalDateTime()
        }
      }
    }
    logger.info { "Teachers see the ${if (studentView) "student" else "teacher"} view" }
  }

  /** The number of moves teachers made in the student view, which the class pages count. */
  fun countedTeacherMoves(): Long =
    transaction {
      UserChoiceTable
        .selectAll()
        .where { UserChoiceTable.countedAsStudent eq true }
        .count()
    }

  /**
   * Deletes every move of each user whose address is in [teacherEmails] and sends their walk back to the first slide,
   * as each pressing their own reset would. It also deletes every counted move left, made by someone taken off
   * [teacherEmails] since, so the class pages count no teacher's move once it is done. Students' moves are never
   * counted ones, so none is deleted. Returns the number of moves deleted.
   */
  fun clearTeacherWalks(teacherEmails: Set<String>): Int =
    transaction {
      val teachers =
        UsersTable
          .select(UsersTable.id)
          .where { UsersTable.email inList teacherEmails }
          .map { it[UsersTable.id].value }

      val deleted =
        UserChoiceTable.deleteWhere {
          (UserChoiceTable.userUuidRef inList teachers) or (UserChoiceTable.countedAsStudent eq true)
        }

      UsersTable.update({ UsersTable.id inList teachers }) { row -> row[lastPathName] = ROOT }

      logger.info { "Cleared $deleted moves of ${teachers.size} teachers" }
      deleted
    }
}

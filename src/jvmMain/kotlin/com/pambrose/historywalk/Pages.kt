package com.pambrose.historywalk

import com.pambrose.historywalk.EndPoints.ADMIN_CLEAR
import com.pambrose.historywalk.EndPoints.ADMIN_VIEW
import com.pambrose.historywalk.EndPoints.REASONS
import com.pambrose.historywalk.EndPoints.SLIDE
import com.pambrose.historywalk.EndPoints.SUMMARY
import com.pambrose.historywalk.HistoryWalkServer.masterSlides
import com.pambrose.historywalk.dbms.AdminTxs.countedTeacherMoves
import com.pambrose.historywalk.dbms.AdminTxs.teachersSeeStudentView
import com.pambrose.historywalk.dbms.DbmsTxs.allSuccessUsers
import com.pambrose.historywalk.dbms.DbmsTxs.allUserReasons
import com.pambrose.historywalk.dbms.DbmsTxs.allUserSummaries
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/** The pages the server renders for teachers: the admin page, class summary, student reasons, and slide list. */
object Pages {
  // The admin page's switch form: its one field names the view to set
  const val VIEW_FIELD = "view"
  const val STUDENT_VIEW = "student"
  const val TEACHER_VIEW = "teacher"

  // The class pages' headings; their browser tabs, which open apart from the walk, show them with the app's name
  const val SUMMARY_TITLE = "Class Summary"
  const val REASONS_TITLE = "Student Reasons"

  /**
   * The switch that lets teachers walk as students do, and the clear. The switch's button names what pressing it does,
   * so the page also says which view teachers see now. In the student view a teacher's walk has no link here, so the
   * page says where it is.
   */
  fun displayAdmin(): String {
    val studentView = teachersSeeStudentView()
    val counted = countedTeacherMoves()
    return createHTML()
      .html {
        head {
          meta(charset = "utf-8")
          title { +"History Walk admin" }
          style {
            unsafe {
              +"body {font-family: sans-serif; margin: 2em; max-width: 40em;}"
              +"form {margin: 1em 0 2em 0;}"
              +"button {font-size: 1em; padding: 0.4em 1em;}"
            }
          }
        }
        body {
          p { a(href = "/") { +"Back to the walk (${if (studentView) STUDENT_VIEW else TEACHER_VIEW} view)" } }
          h2 { +"History Walk admin" }

          if (studentView) {
            p {
              +"Teachers currently see the student view. Each teacher walks as a student does, and their moves count "
              +"in the class summary and student reasons like a student's. A teacher's walk has no link to this page "
              +"in the student view: come back to /admin to switch back."
            }
          } else {
            p {
              +"Teachers currently see the teacher view. Their walks are left out of the class summary and student "
              +"reasons. A move a teacher has already made is not asked about again, so it is not counted when made "
              +"again in the student view: clear the teachers' walks before a trial run."
            }
          }
          form(action = "/$ADMIN_VIEW", method = FormMethod.post) {
            hiddenInput(name = VIEW_FIELD) { value = if (studentView) TEACHER_VIEW else STUDENT_VIEW }
            button(type = ButtonType.submit) {
              +(if (studentView) "Teachers see teacher view" else "Teachers see student view")
            }
          }

          p { +"Teacher moves counted in the class pages: $counted" }
          p {
            +"Clearing deletes every teacher's walk, in either view, and sends each teacher back to the first slide. "
            +"Students' walks are kept."
          }
          form(action = "/$ADMIN_CLEAR", method = FormMethod.post) {
            onSubmit = "return confirm('Delete every walk teachers have made, and send each teacher back to the " +
              "first slide? Student walks are kept.')"
            button(type = ButtonType.submit) { +"Clear teachers' trial walks" }
          }

          p {
            a(href = "/$SUMMARY") { +"class summary" }
            +" · "
            a(href = "/$REASONS") { +"student reasons" }
          }
        }
      }
  }

  /**
   * The student reasons page: every reason given, grouped by user, each with its move and choice. It reads the progress
   * views, so it holds students' reasons and the teachers' ones counted in the student view.
   */
  fun displayUserReasons() =
    createHTML()
      .html {
        head {
          meta(charset = "utf-8")
          title { +"$REASONS_TITLE · History Walk" }
          style {
            unsafe {
              +"table, th, td {border: 1px solid black; border-collapse: collapse;}"
              +"th, td {padding: 5px;}"
            }
          }
        }
        body {
          h2 { +REASONS_TITLE }
          div {
            allUserReasons()
              .groupBy { it.email }
              .forEach { (email, userReasons) ->

                p {
                  b {
                    +"User: "
                  }
                  +email
                }

                table {
                  // style = "border-collapse: separate; border-spacing: 10px 5px;"
                  tr {
                    style = "border: 1px solid black;"
                    th {
                      style = "text-align:left;"
                      +"From/To"
                    }
                    // The slide a choice leads to does not always say which it was: every wrong Moses answer leads to
                    // an Incorrect slide
                    th {
                      style = "text-align:left;"
                      +"Choice"
                    }
                    th {
                      style = "text-align:left;"
                      +"Reason"
                    }
                  }
                  for (userReason in userReasons) {
                    tr {
                      td {
                        +"${userReason.fromTitle} -> ${userReason.toTitle}"
                      }
                      td {
                        +userReason.choiceText
                      }
                      td {
                        +userReason.reason
                      }
                    }
                    tr {}
                  }
                }
                br {}
              }
          }
        }
      }

  /**
   * The class summary page: each user's name, address, decision count, whether they reached the success slide, and
   * last slide, fewest decisions first. It reads the progress views, so a teacher appears only with moves counted in
   * the student view, and only those count.
   */
  fun displayUserSummary() =
    createHTML()
      .html {
        head {
          meta(charset = "utf-8")
          title { +"$SUMMARY_TITLE · History Walk" }
        }
        body {
          h2 { +SUMMARY_TITLE }
          div {
            table {
              style = "border-collapse: separate; border-spacing: 10px 5px;"
              tr {
                th {
                  style = "text-align:left;"
                  +"Name"
                }
                th {
                  style = "text-align:left;"
                  +"Email"
                }
                th {
                  +"Decisions"
                }
                th {
                  style = "text-align:left;"
                  +"Success"
                }
                th {
                  style = "text-align:left;"
                  +"Last Slide"
                }
              }

              // Find all users that have seen the success slide
              val successMoves = allSuccessUsers(masterSlides.successSlide.title).map { it.id }

              allUserSummaries()
                .sortedBy { it.decisionCount }
                .forEach { summary ->
                  tr {
                    td {
                      +summary.fullName
                    }
                    td {
                      +summary.email
                    }
                    td {
                      style = "text-align:center;"
                      +summary.decisionCount.toString()
                    }
                    td {
                      +successMoves.contains(summary.id).toString()
                    }
                    td {
                      +summary.lastPathName
                    }
                  }
                }
            }
          }
        }
      }

  /**
   * The slide list (`/slides`, served only with `ALLOW_SLIDE_ACCESS`): each slide id with its title and a link to each
   * place the slide appears in the deck, which moves the teacher's walk there.
   */
  fun displayAllSlides() =
    createHTML()
      .html {
        body {
          div {
            // style = "float:left;width:50%;"
            table {
              style = "width:100%;border-collapse: separate;border-spacing: 10px 5px;text-align: left"
              tr {
                th { +"ID" }
                th { +"Title" }
                th { +"Instances" }
              }
              masterSlides.slideIdMap
                .toSortedMap()
                .filter { it.key != -1 }
                .forEach { (slideId, slides) ->
                  tr {
                    td {
                      style = "text-align:right;"
                      +"$slideId:"
                    }
                    td {
                      style = "width:25%;"
                      a {
                        href = "/$SLIDE/$slideId"
                        +" ${slides[0].title}"
                      }
                    }
                    td {
                      slides.forEachIndexed { i, _ ->
                        a {
                          href = "/$SLIDE/$slideId/$i"
                          +" ${i + 1}"
                        }
                        +" "
                      }
                    }
                  }
                }
            }
          }
        }
      }
}

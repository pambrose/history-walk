package com.github.pambrose

import com.codahale.metrics.jvm.ThreadDump
import com.github.pambrose.ContentService.Companion.deleteChoices
import com.github.pambrose.ContentService.Companion.updateLastSlide
import com.github.pambrose.EndPoints.CONTENT_RESET
import com.github.pambrose.EndPoints.LOGIN
import com.github.pambrose.EndPoints.LOGOUT
import com.github.pambrose.EndPoints.USER_RESET
import com.github.pambrose.HistoryWalkServer.masterSlides
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.slides.SlideDeck.Companion.ROOT
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.ContentType.Text.Plain
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.kvision.remote.applyRoutes
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import mu.KLogging
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import kotlin.text.Charsets.UTF_8

object Routes : KLogging() {

  fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }

  fun Route.assignRoutes() {
    applyRoutes(RegisterUserServiceManager)

    authenticate {
      applyRoutes(ContentServiceManager)

      post(LOGIN) {
        val principal = call.principal<UserPrincipal>()
        val result =
          if (principal != null) {
            val user = User.queryUserByUuid(principal.uuid)
            if (user.isNotNull()) {
              val userId = UserId(user.uuid.toString())
              call.sessions.set(userId)
              HttpStatusCode.OK
            }
            else {
              HttpStatusCode.Unauthorized
            }
          }
          else {
            HttpStatusCode.Unauthorized
          }

        call.respond(result)
      }

      get(LOGOUT) {
        call.sessions.clear<UserId>()
        call.respondRedirect("/")
      }

      get(CONTENT_RESET) {
        try {
          masterSlides = loadSlides()
          call.respondRedirect("/")
        } catch (e: Exception) {
          logger.error("Error resetting slides", e)
          call.respondText(e.stackTraceToString(), Plain)
        }
      }

      get(USER_RESET) {
        transaction {
          val uuid = call.userId.uuid
          deleteChoices(uuid)
          updateLastSlide(uuid, ROOT)
        }
        call.respondRedirect("/")
      }

      if (EnvVar.ALLOW_SLIDE_ACCESS.getEnv(false)) {
        get("slide/{slideId}/{version?}") {
          val slideId = call.parameters.getOrFail("slideId").toInt()
          val version =
            try {
              call.parameters.getOrFail("version").toInt()
            } catch (e: Exception) {
              0
            }
          val slide = masterSlides.findSlideById(slideId, version)
          if (slide != null) {
            transaction {
              val uuid = call.userId.uuid
              updateLastSlide(uuid, slide.pathName)
            }
            call.respondRedirect("/")
          }
          else {
            call.respondText("Slide not found: $slideId/$version", Plain)
          }
        }

        get("slides") {
          respondWith {
            createHTML()
              .html {
                body {
                  div {
                    //style = "float:left;width:50%;"
                    table {
                      style = "width:100%;border-collapse: separate; border-spacing: 10px 5px;"
                      tr {
                        th { +"ID" }
                        th { +"Title" }
                        th { +"Instances" }
                      }
                      masterSlides.slideIdMap
                        .toSortedMap()
                        .filter { it.key != -1 }
                        .forEach { slideId, slides ->
                          tr {
                            td {
                              style = "text-align:right;"
                              +"$slideId:"
                            }
                            td {
                              style = "width:25%;"
                              a { href = "/slide/$slideId"; +" ${slides[0].title}" }
                            }
                            td {
                              //style = "padding-right:15px;"
                              slides.forEachIndexed { i, slide ->
                                a { href = "/slide/$slideId/$i"; +" $i" }
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
        }
      }
    }

    get("ping") {
      call.respondText("pong", Plain)
    }

    get("version") {
      call.respondText(HistoryWalkServer::class.versionDesc(), Plain)
    }

    get("threaddump") {
      try {
        ByteArrayOutputStream()
          .apply {
            use { ThreadDumpInfo.threadDump.dump(true, true, it) }
          }.let { baos ->
            String(baos.toByteArray(), UTF_8)
          }
      } catch (e: NoClassDefFoundError) {
        "Sorry, your runtime environment does not allow dump threads."
      }.also {
        call.respondText(it, Plain)
      }
    }
  }

  private object ThreadDumpInfo {
    val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
  }
}
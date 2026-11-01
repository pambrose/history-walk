package com.pambrose.historywalk

import com.codahale.metrics.jvm.ThreadDump
import com.pambrose.common.response.respondWith
import com.pambrose.common.util.Version.Companion.versionDesc
import com.pambrose.historywalk.EndPoints.REASONS
import com.pambrose.historywalk.EndPoints.SLIDE
import com.pambrose.historywalk.EndPoints.SUMMARY
import com.pambrose.historywalk.EndPoints.USER_RESET
import com.pambrose.historywalk.EnvVar.ALLOW_SLIDE_ACCESS
import com.pambrose.historywalk.HistoryWalkServer.masterSlides
import com.pambrose.historywalk.Pages.displayAllSlides
import com.pambrose.historywalk.Pages.displayUserReasons
import com.pambrose.historywalk.Pages.displayUserSummary
import com.pambrose.historywalk.auth.GOOGLE_AUTH
import com.pambrose.historywalk.auth.SchoolConfig
import com.pambrose.historywalk.auth.signInRoutes
import com.pambrose.historywalk.auth.signedInApi
import com.pambrose.historywalk.auth.teachersOnly
import com.pambrose.historywalk.auth.userUuid
import com.pambrose.historywalk.dbms.DbmsTxs.deleteChoices
import com.pambrose.historywalk.dbms.DbmsTxs.updateLastSlide
import com.pambrose.historywalk.slides.SlideDeck.Companion.ROOT
import dev.kilua.rpc.applyRoutes
import io.ktor.http.CacheControl
import io.ktor.http.ContentType.Text.Plain
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.net.URL
import java.util.UUID
import kotlin.text.Charsets.UTF_8

object Routes {
  /**
   * The browser checks with the server each time it uses what carries this, and a shared cache such as Cloudflare keeps
   * no copy. `main.bundle.js` has the same name in every release, so without it a cache would go on serving an old
   * bundle after a deploy: given no Cache-Control, Cloudflare keeps a .js file for hours and tells browsers to keep it
   * too. `private` is what keeps Cloudflare from storing it (`cf-cache-status: BYPASS`); Cloudflare passes the header on
   * unchanged.
   */
  internal val CHECKED_EACH_TIME = CacheControl.NoCache(CacheControl.Visibility.Private)

  /**
   * Static content goes out with [CHECKED_EACH_TIME] and an ETag, a SHA-256 of the file's bytes worked out once a file.
   * The browser's check is then a 304 with no body until a release changes the file. The 304 needs `ConditionalHeaders`
   * (`Installs.installConditionalHeaders`).
   */
  internal fun StaticContentConfig<URL>.revalidatedEachTime() {
    cacheControl { listOf(CHECKED_EACH_TIME) }
    etag(ETagProvider.StrongSha256)
  }

  /**
   * The client and the slide images. The jar's `assets/` holds the client: `/` is its `index.html`, the page shell,
   * and `/main.bundle.js` the bundle it loads. Kilua's own static handling (`initRpc(initStaticResources = false)` in
   * Main.kt) is off, since it sends these with no Cache-Control and answers every unknown path with the page shell.
   */
  fun Route.staticContent() {
    staticResources("/", "assets") { revalidatedEachTime() }
    staticResources("/static", "static") { revalidatedEachTime() }
  }

  fun Application.assignRoutes() {
    routing { appRoutes() }
  }

  /**
   * Every route, by who may reach it:
   * - sign-in and sign-out, open to all ([signInRoutes]);
   * - the RPC services, for any signed-in user, refusing a call without a session with 401 ([signedInApi]);
   * - the user's own reset, for any signed-in user, sending a browser without a session to Google sign-in;
   * - the class pages, slide tools, and thread dump, for teachers only, refusing anyone else with 403 ([teachersOnly]);
   * - health, version, and static content ([staticContent]), open to all.
   */
  fun Route.appRoutes(
    allowSlideAccess: Boolean = ALLOW_SLIDE_ACCESS.getEnv(false),
    findEmail: (UUID) -> String? = { User.emailForUuid(it) },
    teacherEmails: () -> Set<String> = { SchoolConfig.teacherEmails },
  ) {
    signInRoutes()

    signedInApi {
      authenticate(GOOGLE_AUTH) {
        applyRoutes(ContentServiceManager)
      }
    }

    authenticate(GOOGLE_AUTH) {
      get(USER_RESET) {
        transaction {
          val uuid = call.userUuid
          deleteChoices(uuid)
          updateLastSlide(uuid, ROOT)
        }
        call.respondRedirect("/")
      }

      teachersOnly(findEmail, teacherEmails) {
        get(SUMMARY) {
          respondWith { displayUserSummary() }
        }

        get(REASONS) {
          respondWith { displayUserReasons() }
        }

        // Every JVM thread's stack; taking it briefly pauses the JVM
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

        if (allowSlideAccess) {
          get("$SLIDE/{slideId}/{version?}") {
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
                val uuid = call.userUuid
                updateLastSlide(uuid, slide.pathName)
              }
              call.respondRedirect("/")
            } else {
              call.respondText("Slide not found: $slideId/$version", Plain)
            }
          }

          get("slides") {
            respondWith { displayAllSlides() }
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

    staticContent()
  }

  private object ThreadDumpInfo {
    val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
  }
}

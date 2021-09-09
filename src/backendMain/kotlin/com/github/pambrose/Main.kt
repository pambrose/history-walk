package com.github.pambrose

import com.github.pambrose.BrowserSessions.assignBrowserSession
import com.github.pambrose.Db.dbQuery
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.randomId
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.*
import io.kvision.remote.applyRoutes
import io.kvision.remote.kvisionInit
import mu.KLogging
import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import kotlin.collections.set
import kotlin.time.Duration

const val AUTH_COOKIE = "auth"

fun Application.main() {
  Content.initContent()
  Slide.verifySlides()

  install(Compression)
  install(DefaultHeaders)
  install(CallLogging)

  install(Sessions) {
    cookie<Profile>("KTSESSION", storage = SessionStorageMemory()) {
      cookie.path = "/"
      cookie.extensions["SameSite"] = "strict"
      cookie.maxAgeInSeconds = Duration.days(14).inWholeSeconds
    }

    cookie<BrowserSession>("history_session_id") {
      cookie.path = "/"
      cookie.httpOnly = true

      // CSRF protection in modern browsers. Make sure your important side-effect-y operations, like ordering,
      // uploads, and changing settings, use "unsafe" HTTP verbs like POST and PUT, not GET or HEAD.
      // https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#SameSite_cookies
      cookie.extensions["SameSite"] = "lax"
    }

    cookie<UserPrincipal>(AUTH_COOKIE) {
      cookie.path = "/" //CHALLENGE_ROOT + "/"
      cookie.httpOnly = true
      //if (production)
      //  cookie.secure = true
      cookie.maxAgeInSeconds = Duration.days(14).inWholeSeconds

      // CSRF protection in modern browsers. Make sure your important side-effect-y operations, like ordering,
      // uploads, and changing settings, use "unsafe" HTTP verbs like POST and PUT, not GET or HEAD.
      // https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#SameSite_cookies
      cookie.extensions["SameSite"] = "lax"
    }
  }

  Db.init(environment.config)

  install(Authentication) {
    form("UserId") {
      userParamName = "username"
      passwordParamName = "password"

      validate { credentials ->
        dbQuery {
          UserDao.select {
            (UserDao.username eq credentials.name) and (UserDao.password eq DigestUtils.sha256Hex(credentials.password))
          }.firstOrNull()?.let {
            UserIdPrincipal(credentials.name)
          }
        }
      }

      skipWhen { call -> call.sessions.get<Profile>() != null }
    }
  }

  routing {
    applyRoutes(RegisterProfileServiceManager)

    authenticate("UserId") {
      post("login") {
        val principal = call.principal<UserIdPrincipal>()
        val result =
          if (principal != null) {
            dbQuery {
              UserDao.select { UserDao.username eq principal.name }.firstOrNull()?.let {
                val profile =
                  Profile(it[UserDao.id], it[UserDao.name], it[UserDao.username].toString(), null, null)
                call.sessions.set(profile)
                HttpStatusCode.OK
              } ?: HttpStatusCode.Unauthorized
            }
          } else {
            HttpStatusCode.Unauthorized
          }
        assignBrowserSession()

        call.respond(result)
      }

      get("logout") {
        call.sessions.clear<Profile>()
        call.respondRedirect("/")
      }

      applyRoutes(AddressServiceManager)
      applyRoutes(ProfileServiceManager)

      applyRoutes(ContentServiceManager)
    }
  }

  kvisionInit()
}


typealias PipelineCall = PipelineContext<Unit, ApplicationCall>

object BrowserSessions : KLogging() {
  fun PipelineCall.assignBrowserSession() {
//  if (call.request.headers.contains(NO_TRACK_HEADER))
//    return

    if (call.browserSession.isNull()) {
      val browserSession = BrowserSession(id = randomId(15))
      call.sessions.set(browserSession)

//    if (isSaveRequestsEnabled()) {
//      val ipAddress = call.request.origin.remoteHost
//      try {
//        lookupGeoInfo(ipAddress)
//      } catch (e: Throwable) {
//        logger.warn(e) {}
//      }
//    }

      logger.debug { "Created browser session: ${browserSession.id} - ${call.request.origin.remoteHost}" }
    }
  }
}

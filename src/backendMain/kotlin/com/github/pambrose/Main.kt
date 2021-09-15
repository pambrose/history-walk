package com.github.pambrose

import com.github.pambrose.BrowserSessions.assignBrowserSession
import com.github.pambrose.Property.Companion.assignProperties
import com.github.pambrose.User.Companion.queryUserByEmail
import com.github.pambrose.User.Companion.queryUserByUuid
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.randomId
import com.github.pambrose.common.util.sha256
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
import kotlin.collections.set
import kotlin.time.Duration

const val AUTH_COOKIE = "auth"

fun Application.main() {

  assignProperties()

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

      validate { cred ->
        var principal: UserPrincipal? = null
        val user = queryUserByEmail(Email(cred.name))
        if (user.isNotNull()) {
          val salt = user.salt
          val digest = user.digest
          if (salt.isNotBlank() && digest.isNotBlank() && digest == cred.password.sha256(salt)) {
            //logger.debug { "Found user ${cred.name} ${user.userId}" }
            principal = UserPrincipal(user.uuid)
          }
        }

        //logger.info { "Login ${if (principal.isNull()) "failure" else "success for $user ${user?.email ?: UNKNOWN}"}" }

        principal
      }

      skipWhen { call -> call.sessions.get<Profile>().isNotNull() }
    }
  }

  routing {
    applyRoutes(RegisterProfileServiceManager)

    authenticate("UserId") {
      applyRoutes(ContentServiceManager)

      post("login") {
        val principal = call.principal<UserPrincipal>()
        val result =
          if (principal != null) {
            val user = queryUserByUuid(principal.uuid)
            if (user.isNotNull()) {
              val profile = Profile(user.uuid.toString(), user.fullName.value, user.email.value, "", "")
              call.sessions.set(profile)
              HttpStatusCode.OK
            } else {
              HttpStatusCode.Unauthorized
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

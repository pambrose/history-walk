package com.pambrose.historywalk.auth

import com.pambrose.historywalk.Role
import com.pambrose.historywalk.User
import com.pambrose.historywalk.Utils.toUuid
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.isHandled
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import java.util.UUID

/**
 * Answers a call carrying no user session with 401 rather than letting the OAuth provider redirect it to Google
 * sign-in. Nothing behind this guard can follow that redirect: Kilua turns only a 401 into the `SecurityException`
 * that raises the sign-in prompt, and its fetch fails on the redirect instead. It runs with the plugins, ahead of
 * authentication.
 */
private val SignedInApiGuard =
  createRouteScopedPlugin("SignedInApiGuard") {
    onCall { call ->
      if (call.signedInUserUuid() == null)
        call.respond(HttpStatusCode.Unauthorized)
    }
  }

private class TeachersOnlyConfig {
  var findEmail: (UUID) -> String? = { User.emailForUuid(it) }
  var teacherEmails: () -> Set<String> = { SchoolConfig.teacherEmails }
}

/**
 * Answers 403 `Teachers only` unless the signed-in user is a teacher. It runs once authentication is checked, so a
 * browser with no session has been sent to Google sign-in already, and is not told it is not a teacher.
 */
private val TeachersOnlyGuard =
  createRouteScopedPlugin("TeachersOnlyGuard", ::TeachersOnlyConfig) {
    val findEmail = pluginConfig.findEmail
    val teacherEmails = pluginConfig.teacherEmails
    on(AuthenticationChecked) { call ->
      // Authentication answered the call itself: the redirect to Google sign-in
      if (call.isHandled) return@on
      if (!call.isTeacher(findEmail, teacherEmails()))
        call.respondText("Teachers only", ContentType.Text.Plain, HttpStatusCode.Forbidden)
    }
  }

private fun ApplicationCall.isTeacher(
  findEmail: (UUID) -> String?,
  teacherEmails: Set<String>,
): Boolean {
  val email = signedInUserUuid()?.let { findEmail(it.toUuid()) } ?: return false
  return Roles.roleFor(email, teacherEmails) == Role.TEACHER
}

/**
 * Adds the routes [build] creates -- the RPC services -- beneath a path-less child route carrying [SignedInApiGuard],
 * so the guard runs for exactly those routes. A guard installed on a sibling route never runs: routing only passes a
 * call through the routes that lead to its handler.
 */
fun Route.signedInApi(build: Route.() -> Unit): Route =
  createChild(GuardSelector("signed-in API guard")).apply {
    install(SignedInApiGuard)
    build()
  }

/**
 * Adds the routes [build] creates beneath a path-less child route carrying [TeachersOnlyGuard]: only a signed-in user
 * whose address [findEmail] finds and the role rule makes a teacher, by [teacherEmails], reaches them.
 */
fun Route.teachersOnly(
  findEmail: (UUID) -> String? = { User.emailForUuid(it) },
  teacherEmails: () -> Set<String> = { SchoolConfig.teacherEmails },
  build: Route.() -> Unit,
): Route =
  createChild(GuardSelector("teachers-only guard")).apply {
    install(TeachersOnlyGuard) {
      this.findEmail = findEmail
      this.teacherEmails = teacherEmails
    }
    build()
  }

/**
 * What [noStore] sends. `no-store` keeps the browser from storing a copy in its HTTP cache, and from restoring one from
 * its back/forward cache once sign-out has changed the session cookie, so Back after sign-out asks the server again
 * rather than showing the class's data. `private` keeps a shared cache from storing it: Cloudflare keeps no copy, even
 * under a "cache everything" rule, unless that rule is set to ignore the origin's Cache-Control.
 */
private val NOT_STORED = CacheControl.NoStore(CacheControl.Visibility.Private)

/**
 * Sends [NOT_STORED] on every response of the routes beneath it. It sets the header as the call starts, ahead of
 * authentication and the guards, so the redirect to Google sign-in, a guard's refusal, and a handler's page or
 * redirect all carry it: each of them only adds to the response's headers.
 */
private val NoStore =
  createRouteScopedPlugin("NoStore") {
    onCall { call -> call.response.cacheControl(NOT_STORED) }
  }

/**
 * Adds the routes [build] creates beneath a path-less child route carrying [NoStore]: for the pages and redirects that
 * belong to one signed-in user, which must not outlive that user's sign-out on a shared computer.
 */
fun Route.noStore(build: Route.() -> Unit): Route =
  createChild(GuardSelector("no-store")).apply {
    install(NoStore)
    build()
  }

/**
 * Matches without consuming a path segment, the way `authenticate { }` does. Not a data class: selectors are compared
 * to find an existing child route, so each guard gets a child of its own rather than another guard's.
 */
private class GuardSelector(
  private val name: String,
) : RouteSelector() {
  override suspend fun evaluate(
    context: RoutingResolveContext,
    segmentIndex: Int,
  ): RouteSelectorEvaluation = RouteSelectorEvaluation.Transparent

  override fun toString() = "($name)"
}

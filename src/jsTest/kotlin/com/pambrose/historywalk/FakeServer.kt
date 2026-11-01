package com.pambrose.historywalk

import dev.kilua.rpc.RpcSerialization
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import org.w3c.fetch.Request
import org.w3c.fetch.Response
import org.w3c.fetch.ResponseInit

/** One RPC call the page made: its route, and its parameters, each the JSON Kilua sent. */
class RpcCall(
  val route: String,
  val params: List<String?>,
) {
  /** Parameter [index], decoded as Kilua's server decodes it. */
  inline fun <reified T> param(index: Int): T = RpcSerialization.getJson().decodeFromString(params[index]!!)
}

/** How [FakeServer] answers a call. */
sealed interface Reply {
  /** The call returns the result [json] encodes (see [returns]). */
  class Result(
    val json: String,
  ) : Reply

  /** The server refuses the call, as ContentService's checks do: the page gets a `ServiceException(message)`. */
  class Refused(
    val message: String,
  ) : Reply

  /** An HTTP error: a 401 reaches the page as a `SecurityException`, any other status as a plain `Exception`. */
  class HttpError(
    val status: Int,
    val statusText: String,
  ) : Reply
}

/** A reply returning [value], encoded as Kilua's server encodes a result. */
inline fun <reified T> returns(value: T): Reply = Reply.Result(RpcSerialization.getJson().encodeToString(value))

/** The route Kilua posts [function] (an `IContentService` member, e.g. `IContentService::getCurrentSlide`) to. */
fun routeOf(function: Function<*>): String = ContentServiceManager.requireCall(function).first

/**
 * Stands in for the server in the client specs, so a call takes the page's real path: Rpc's `withAuth`, Kilua's client
 * and its reading of the response, as a result, a `ServiceException`, a `SecurityException`, or an `Exception`. Kilua
 * posts each call with the browser's `fetch`, which [install] replaces.
 *
 * Each route has a handler, which may suspend, to hold a call in flight. [reset] restores the defaults: the user is
 * [testStudent] and is on [testSlide]; any other call fails with a 500. `getUserInfo` and `getCurrentSlide` answer by
 * default because `MainPanel` makes the first as it starts and the second after a refusal, and until a first call
 * succeeds KVision's `SecurityMgr.withAuth` takes any failure for a signed-out user and parks the call behind the
 * sign-in dialog for good.
 */
object FakeServer {
  private val handlers = mutableMapOf<String, suspend (RpcCall) -> Reply>()
  private var installed = false

  /** Every call the page has made since the last [reset], oldest first. */
  val calls = mutableListOf<RpcCall>()

  init {
    reset()
  }

  /** Replaces the page's `fetch` with this server; a request that is not an RPC call gets a 404. Idempotent. */
  fun install() {
    if (installed) return
    installed = true
    window.asDynamic().fetch = { input: dynamic, init: dynamic ->
      val request = if (input is Request) input else Request(input, init ?: js("({})"))
      CoroutineScope(Dispatchers.Default).promise { respond(request) }
    }
  }

  /** Answers every call to [function]'s route with [handler], until the next [reset]. */
  fun on(
    function: Function<*>,
    handler: suspend (RpcCall) -> Reply,
  ) {
    install()
    handlers[routeOf(function)] = handler
  }

  /** The calls made to [function]'s route since the last [reset]. */
  fun callsTo(function: Function<*>): List<RpcCall> = calls.filter { it.route == routeOf(function) }

  /** Forgets the calls and the handlers the specs set, and restores the defaults (see above). */
  fun reset() {
    install()
    calls.clear()
    handlers.clear()
    handlers[routeOf(IContentService::getUserInfo)] = { returns(testStudent) }
    handlers[routeOf(IContentService::getCurrentSlide)] = { returns(testSlide()) }
  }

  private suspend fun respond(request: Request): Response {
    if ("/rpc/" !in request.url) return response(404, "Not Found", null)
    val rpc = JSON.parse<dynamic>(request.text().await())
    val id = rpc.id.unsafeCast<Int>()
    val call = RpcCall(rpc.method.unsafeCast<String>(), rpc.params.unsafeCast<Array<String?>>().toList())
    calls += call
    val reply = handlers[call.route]?.invoke(call) ?: Reply.HttpError(500, "No answer for ${call.route}")
    val body = js("({})")
    body.id = id
    return when (reply) {
      is Reply.Result -> {
        body.result = reply.json
        response(200, "OK", JSON.stringify(body))
      }

      is Reply.Refused -> {
        body.error = reply.message
        body.exceptionType = "dev.kilua.rpc.ServiceException"
        response(200, "OK", JSON.stringify(body))
      }

      is Reply.HttpError -> {
        response(reply.status, reply.statusText, null)
      }
    }
  }

  private fun response(
    status: Int,
    statusText: String,
    json: String?,
  ): Response {
    val init = js("({})")
    init.status = status
    init.statusText = statusText
    if (json != null) init.headers = js("({ 'content-type': 'application/json' })")
    return Response(json, init.unsafeCast<ResponseInit>())
  }
}

/** The student the fake server signs in. */
val testStudent = UserInfo("Ada Lovelace", "26alovelace@athenian.org")

/** A choice leading on from [testSlide]; an [offset] below zero makes it a go-back. */
fun testChoice(
  text: String,
  offset: Int = 0,
) = SlideChoice(
  choiceText = text,
  toPathName = "/Start/$text",
  toTitle = text,
  deadEnd = false,
  offset = offset,
  alreadyVisited = false,
)

/** A slide titled [title] at the root of the deck, offering [choices]. */
fun testSlide(
  title: String = "Start",
  choices: List<SlideChoice> = listOf(testChoice("Go north"), testChoice("Go south")),
  pathName: String = "/$title",
  parentTitles: List<ParentTitle> = emptyList(),
) = SlideData(
  pathName = pathName,
  title = title,
  content = "<p>The $title slide</p>",
  success = false,
  choices = choices,
  verticalChoices = false,
  parentTitles = parentTitles,
  offset = 0,
  displayTitle = true,
  decisionCount = 0,
  displayConsecutiveCorrectDecisions = false,
  consecutiveCorrectDecisions = 0,
  showResetButton = false,
)

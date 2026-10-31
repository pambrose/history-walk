package com.github.pambrose

import com.github.pambrose.EndPoints.LOGIN
import dev.kilua.rpc.SecurityException
import io.kvision.remote.SecurityMgr
import io.kvision.rest.HttpMethod
import io.kvision.rest.ResponseBodyType
import io.kvision.rest.RestClient
import io.kvision.rest.requestDynamic
import io.kvision.utils.obj
import kotlinx.coroutines.asDeferred

/**
 * Form login dispatcher (KVision 9 no longer ships io.kvision.remote.LoginService).
 */
class LoginService(
  private val loginEndpoint: String,
) {
  private val loginAgent = RestClient()

  suspend fun login(credentials: Credentials?): Boolean =
    if (credentials?.username != null) {
      loginAgent.requestDynamic(loginEndpoint) {
        data = obj {
          this.username = credentials.username
          this.password = credentials.password
        }
        method = HttpMethod.POST
        contentType = "application/x-www-form-urlencoded"
        responseBodyType = ResponseBodyType.READABLE_STREAM
      }.then { _: dynamic -> true }.asDeferred().await()
    } else {
      throw SecurityException("Credentials cannot be empty")
    }
}

object Security : SecurityMgr() {
  private val loginService = LoginService("/$LOGIN")
  private val loginPanel = LoginPanel()

  override suspend fun login() = loginService.login(loginPanel.getResult())

  override suspend fun afterLogin() {
    console.log("Successful login")
  }

  override suspend fun afterError() {
    console.log("Error on login")
  }
}

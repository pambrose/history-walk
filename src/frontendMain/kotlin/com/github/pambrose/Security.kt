package com.github.pambrose

import com.github.pambrose.EndPoints.LOGIN
import io.kvision.remote.LoginService
import io.kvision.remote.SecurityMgr

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
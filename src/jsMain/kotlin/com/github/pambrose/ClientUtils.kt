package com.github.pambrose

import io.kvision.core.CssSize
import io.kvision.core.StyledComponent
import io.kvision.core.TextTransform
import io.kvision.html.Button

object ClientUtils {
  fun Button.lowercase() {
    textTransform = TextTransform.NONE
  }

  fun StyledComponent.verticalPadding(size: CssSize) {
    paddingTop = size
    paddingBottom = size
  }

  suspend fun <T> withAuth(block: suspend () -> T) =
    Security.withAuth {
      try {
        block()
      } catch (e: Exception) {
        console.log("This is the exception: $e ${e.message}")
        // Alert.show(text = I18n.tr(e.toString()))
        throw e
      }
    }
}

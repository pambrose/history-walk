package com.pambrose.historywalk

import com.pambrose.historywalk.ClientUtils.withAuth
import dev.kilua.rpc.getService

/** The page's calls to [IContentService], each through [withAuth], so that only a 401 shows the sign-in dialog. */
object Rpc {
  private val contentService = getService<IContentService>()

  /** The signed-in user ([IContentService.getUserInfo]). */
  suspend fun getUserInfo() =
    withAuth {
      contentService.getUserInfo()
    }

  /** The slide the signed-in user is on ([IContentService.getCurrentSlide]). */
  suspend fun getCurrentSlide() =
    withAuth {
      contentService.getCurrentSlide()
    }

  /** Starts the move to [slideChoice]'s target; an empty reason in the answer means one must be asked for. */
  suspend fun makeChoice(
    fromPathName: String,
    fromTitle: String,
    slideChoice: SlideChoice,
    advance: Boolean,
  ): UserChoice =
    withAuth {
      contentService.makeChoice(fromPathName, fromTitle, slideChoice, advance)
    }

  /** Records the move to [slideChoice]'s target with [reason]; returns its slide ([IContentService.provideReason]). */
  suspend fun provideReason(
    fromPathName: String,
    fromTitle: String,
    slideChoice: SlideChoice,
    reason: String,
  ): SlideData =
    withAuth {
      contentService.provideReason(fromPathName, fromTitle, slideChoice, reason)
    }

  /** Takes the user back to [parentTitle] and returns that slide ([IContentService.goBackInTime]). */
  suspend fun goBackInTime(parentTitle: ParentTitle) =
    withAuth {
      contentService.goBackInTime(parentTitle)
    }
}

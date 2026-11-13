package com.pambrose.historywalk

import dev.kilua.rpc.annotations.RpcService

/**
 * The whole client/server API, answered for the signed-in user (Kilua RPC generates its plumbing). The server takes the
 * deck and the user's stored slide as the truth, and refuses a move they do not allow with a `ServiceException`.
 */
@RpcService
interface IContentService {
  /** The signed-in user, reported as a student when a teacher calls while teachers see the student view. */
  suspend fun getUserInfo(): UserInfo

  /** The slide the signed-in user is on: the root for a new user, or for a stored path name the deck no longer has. */
  suspend fun getCurrentSlide(): SlideData

  /**
   * Starts the forward move to [slideChoice]'s target from [fromPathNameStr], which must be the user's current slide.
   * The answer's reason is empty when the page must ask for one ([provideReason]); otherwise the user has moved on.
   * Only the target's path name is read from [slideChoice]: the deck supplies the rest, and [advance] is ignored.
   */
  suspend fun makeChoice(
    fromPathNameStr: String,
    fromTitleStr: String,
    slideChoice: SlideChoice,
    advance: Boolean,
  ): UserChoice

  /**
   * Records the move to [slideChoice]'s target from [fromPathNameStr] with [reasonStr], which must pass ReasonRule, and
   * returns the slide it leads to. A repeat of a move already recorded changes nothing: the first reason is kept.
   */
  suspend fun provideReason(
    fromPathNameStr: String,
    fromTitleStr: String,
    slideChoice: SlideChoice,
    reasonStr: String,
  ): SlideData

  /** Moves the user back to [parentTitle] (an ancestor of their slide, the slide itself, or `/`) and returns it. */
  suspend fun goBackInTime(parentTitle: ParentTitle): SlideData

  /** Whether the calling page's bundle still matches the server (ServerVersionCheck), polled by ClientVersionCheck. */
  suspend fun versionCheck(versionCheckInfo: VersionCheckInfo): VersionCheckResponse
}

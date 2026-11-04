# Questions and answers

## Signing in

??? question "Google shows my personal Gmail account instead of my school account."

    Google's account chooser lists every account signed in to the browser. Pick your athenian.org account, or choose
    **Use another account** and sign in to it. If Google shows an error page with your Gmail account on it before you
    could choose, see the next question.

??? question "Google says *Access blocked: This app's request is invalid* (Error 400: redirect_uri_mismatch)."

    The app's sign-in settings don't match its Google setup, so Google refuses every sign-in, for everyone. It isn't
    your account. Tell your administrator; [Google sign-in](../admins/google-sign-in.md#redirect_uri_mismatch) has the
    fix.

??? question "I'm a teacher, but I see a student's page."

    Either your address is not on the teacher list (ask your administrator to add it), or the app is in the
    [student view](student-view.md): open `/admin` at the walk's address to check. If `/admin` refuses you, you're
    not on the list.

??? question "A student is told their account *isn't set up to use History Walk*."

    Student addresses must start with two digits, such as `27msmith@athenian.org`. An athenian.org address that
    doesn't (a staff account, or a student account with a different pattern) is refused unless an administrator adds
    it to the teacher list.

??? question "I was asked to sign in again in the middle of class."

    A sign-in lasts 12 hours. Signing out in any browser also ends your sign-in everywhere else. Sign in again; your
    walk is where you left it.

## The walk

??? question "The OK button in the reason dialog won't enable."

    A reason needs at least five words. The counter under the box shows how many it has.

??? question "A student made a choice and wasn't asked why."

    The app asks once per move: a choice the student already made from that slide keeps its first reason. Slides
    with a single way forward, and choices that go back, never ask.

??? question "A student's page shows *Application Update* and a countdown."

    A new version of the app was released while their page was open. Clicking the link, or waiting for the countdown,
    reloads the page; nothing is lost. The reload waits while a reason is being written.

??? question "*Something went wrong, so nothing changed. Please try again.*"

    A click didn't reach the server, usually a brief network problem. Nothing was saved, so clicking again is safe.
    If a reason couldn't be saved, the dialog opens again with the reason still in it.

??? question "Can a student undo a decision?"

    No. Every decision and reason stays recorded. A student can go back to an earlier slide with the green buttons
    above the slide and choose differently; both attempts show in the class pages.

??? question "Can a student start the whole walk over?"

    A student can go back to the first slide at any time with the top green button, but their recorded decisions
    stay. Deleting a student's walk is an administrator's job (see [Database](../admins/database.md)).

## The class pages

??? question "My moves show up in the class summary."

    Someone switched the app to the student view, and your moves were counted like a student's. Open `/admin`, switch
    back to the teacher view, and press **Clear teachers' trial walks**.

??? question "I did a trial run in the student view, but my walk doesn't appear in the class summary."

    Moves you had already made in the teacher view aren't counted again. Clear the teachers' walks, then walk again
    in the student view. See [Trying the walk as a student](student-view.md).

??? question "The class summary doesn't show a student's latest moves."

    The page shows the class as it was when it loaded. Refresh it.

??? question "Can students see the class summary or each other's reasons?"

    No. The class pages refuse anyone who isn't on the teacher list, and nothing on a student's page shows another
    student's walk.

??? question "A student's progress seems to have disappeared."

    Check the summary first: their Decisions count and Last Slide show what the app has. No teacher button deletes a
    student's walk; only an administrator can, in the database. The other way a student loses their place is an
    update of the app that renamed or moved the slide they were on, which sends them back to the first slide (their
    recorded decisions stay). Ask your administrator.

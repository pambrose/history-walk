# The walk

This page describes what a student sees and does. Your page is the same, with a few
[teacher additions](teacher-view.md).

## A slide

Each slide has:

- **The path so far**, above the slide: a green button for each earlier slide on the way here, from the first one
  down, with an arrow between them.
- **A title**, in a blue bar (a few slides leave it out).
- **The story**, in a box. A slide may have pictures.
- **The choices**, as buttons below the story.

At the top of the page are the student's name and address, and their counts:

Total Decisions
:   How many decisions the student has made and explained, over the whole walk, failed attempts included.

Consecutive Correct Decisions
:   How many decisions the student has made since their last decision that led to a dead end (or all of them, if
    none has). A decision counts as leading to a dead end even when a *Continue* slide stands between it and the
    ending, as when leaving in the autumn ends with the Paddy Rollers. An administrator can hide this line.

## Making a choice

1. The student clicks a choice.
2. A **Reasoning** dialog asks for the *Reason for your "…" decision*, with a hint: *I chose "…" because…*
3. The **OK** button stays disabled until the reason has **at least five words**; a counter under the box shows how
   many there are. A reason can be up to 2,000 characters.
4. **OK** saves the reason and moves on to the next slide. **Cancel** stays on the slide, with nothing saved.

If the student's sign-in has run out when they press **OK** (a sign-in lasts 12 hours), the sign-in dialog appears.
The reason isn't lost: once they're signed in again and make the same choice, the dialog opens with it already typed.

Some moves don't ask for a reason:

- **A slide with a single way forward**, such as a *continue*, just moves on. It isn't counted as a decision.
- **A choice the student has made before** from this slide moves on without asking again: the first reason given is
  the one kept. A bookmark icon on a choice button marks the choices already taken from this slide.
- **A choice that goes back**, such as trying another building, returns to an earlier slide without a reason.

If saving fails (a lost connection, say), a message says the reason was not saved and the dialog opens again with the
reason still in it, so nothing typed is lost.

## Dead ends and success

A **dead end** has a red story box and no choices: the attempt has failed. The student uses the green buttons above
the slide to step back to an earlier decision and choose differently. Stepping back is free: it changes no counts
and deletes nothing.

The **success** slide has a green story box. A **go home** link below it returns to the first slide.

## Going back

Clicking any green button above the slide goes back to that slide. The student can then choose again; their earlier
decisions and reasons stay recorded, so the class pages show every path a student tried, not only the last.

## Coming back later

The walk is saved after every move. A student who closes the page, signs out, or signs in from another computer
continues on the slide where they stopped, with their counts and bookmarks as they were.

!!! info "When the app is updated"

    If a new version of History Walk is released while a page is open, the page shows an *Application Update* dialog:
    *A new version of History Walk is available. Please click here to reload this page and continue.* Clicking the
    link reloads the page; nothing is lost, since every move is already saved. If nobody clicks, the page counts down
    and reloads itself about a minute later. It waits while a student is writing a reason, so a half-written reason
    is never thrown away.

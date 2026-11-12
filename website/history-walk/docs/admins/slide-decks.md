# Slide decks

The story is code: each walk is a Kotlin value in `src/jvmMain/kotlin/com/pambrose/historywalk/content/`, compiled
into the jar. Nothing is fetched or edited at runtime, so a change to the story ships with a release.

| Deck | File | Selected by |
|---|---|---|
| Jermain | `JermainLonguen.kt` | `SLIDES_VARIABLE_NAME=jermain`, or unset |
| Moses | `Moses.kt` | `SLIDES_VARIABLE_NAME=moses` |

## How a deck is written

A deck is a tree of slides built with a small DSL. The decks define their slides from the end of the story back to
the start, so each slide's choices can name slides already defined:

```kotlin
slideDeck {
  val errorSlide = slide(id = 99, title = "Incorrect Decision", content = ERROR_TEXT, displayTitle = false) {}
  val slideSuccess = slide(id = 30, title = "Success!", content = SUCCESS_TEXT, success = true)

  val slide29 =
    slide(id = 29, title = "29th Decison", content = DECISION29_TEXT) {
      choice(text = "Stay with the SS Fox", slide = slideSuccess)
      choice(text = "Look for a new ship", slide = errorSlide.copyOf("Incorrect2"))
    }
  // … and so on, up to the first slide, marked root = true
}
```

- **`slide(...)`** takes an `id` (the number `/slides` lists), a `title`, the `content`, and optionally
  `root = true` (the first slide; a deck has exactly one), `success = true` (the success slide; exactly one), and
  `displayTitle = false` (no title bar).
- **`choice(text, slide)`** adds a button leading to a child slide. A slide may have only one parent: to reach the
  same text from two places, use `copyOf("another title")`, which makes a copy with its own title and path. Two
  choices on one slide can't have the same text; the deck refuses to load if they do.
- **Content** is HTML and Markdown in raw strings; the text constants are `SCREAMING_SNAKE_CASE`.
- **A slide with no choices** is a dead end, shown in red, unless it is the success slide, shown in green. The class
  summary's *Success* column counts students who reached a slide with the success slide's title. A decision is
  stored as leading to a dead end when its slide is one, or when that slide's only choice, a *Continue*, leads to one.
- **Images** need an `alt` that describes the picture, which a screen reader reads aloud in its place; a test refuses
  an empty one or `Pic`.
- **A go-back choice**, `choice(text, goBack(-n))`, returns the student to the slide's n-th ancestor (-1 is its
  parent) without asking for a reason. The Jermain deck uses these to try another building in the same town.
- **Images** go in `src/jvmMain/resources/static/images` and are referenced as `/static/images/<name>`.

The deck refuses to load (and the server to start) without exactly one root slide and one success slide, or with a
success slide that has choices.

## Slide identity: path names

The app identifies a slide by its **path name**: the titles from the first slide down to it, such as
`/Introduction/1st Decision/2nd Decision`. Students' progress is stored by path name (where each student is, and every
move they made), so:

!!! danger "Renaming or moving a slide orphans progress"

    Change a slide's title, or move it under another parent, and every path name below it changes. A student who was
    on one of those slides goes back to the first slide at their next visit. Their recorded moves stay, with the old
    names, in the class pages.

To catch this, `SlidePathsTest` pins every path name a student can reach in each deck to a snapshot,
`src/jvmTest/resources/slide-paths/<deck>.txt`. A change that renames or moves a slide fails CI, listing the path names
that disappeared (each one strands the students on it) and those that appeared.

For a deliberate rename, either:

- accept that the students on the affected slides restart, or
- add a [migration](database.md#migrations) that rewrites the stored path names (`users.last_path_name`,
  `user_choices.from_path_name` and `to_path_name`) from the old names to the new ones,

and in either case rewrite the snapshot and commit it with the change:

```bash
./gradlew jvmTest --tests 'com.pambrose.historywalk.content.SlidePathsTest' -PupdateSlidePaths=true
```

Text changes that keep every title, and new slides that add path names without removing any, are safe.

## Checking a deck

`SlideContentTest` walks both decks from the first slide and fails on a path name that doesn't find its slide, a
slide shared between two parents, a go-back past the first slide, a missing image, or a success slide whose title
differs from the deck's.

To read a change before releasing it, run the app locally (see [Development](development.md)) with
`ALLOW_SLIDE_ACCESS=true`, sign in as a teacher, and use `/slides` to jump to the slides you changed.

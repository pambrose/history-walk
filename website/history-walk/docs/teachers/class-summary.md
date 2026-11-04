# Class summary

The class summary (**class summary** at the top right, or `/summary`) has one row for each student who has signed in
to this walk, with where they are and how far they've come.

## The columns

| Column | Shows |
|---|---|
| **Name** | The student's name, from their Google account. |
| **Email** | Their athenian.org address. |
| **Decisions** | How many decisions they have made and explained, failed attempts included: the same number as *Total Decisions* on their page. |
| **Success** | `true` once they have reached the success slide, even if they have since gone back; `false` until then. |
| **Last Slide** | The slide they are on now, written as the path of slide titles from the first slide, such as `/UGRR Scenario/START: Major Decision/Companion Decision`. |

Rows are in order of **Decisions, fewest first**, so students who haven't started, or are just starting, are at the
top. A student who has signed in but not yet decided anything appears with 0 decisions.

## Reading it

- **Who hasn't started?** Rows at the top with 0 decisions. A Last Slide of just `/` means the first slide: the
  student hasn't moved yet, or their walk was started over.
- **Who has finished?** `true` in Success.
- **Who is stuck?** Many decisions without success often means repeated dead ends; their reasons show what they were
  thinking (see [Student reasons](student-reasons.md)).
- **Where is everyone?** Last Slide shows how deep into the story each student is: the more titles in the path, the
  further along.

## Keeping it current

The page shows the class as it was when the page loaded. Refresh the page (++cmd+r++ on a Mac, ++ctrl+r++ on
Windows) to see new moves.

## Who is on it

- Every **student** who has signed in to this walk.
- **Teachers** do not appear, except a teacher who made moves while the app was in the
  [student view](student-view.md): those moves count like a student's, and that teacher appears with only those moves
  in their Decisions. Clearing the teachers' walks on the admin page takes them off again.

The page shows data only from the walk you are signed in to; the other walk has its own summary.

# Teachers and students

Who may sign in, and as what, follows one rule, applied to the address of the Google account:

1. The address must be in the school's domain (`SCHOOL_EMAIL_DOMAIN`, `athenian.org`), and verified by Google.
2. If it is in `TEACHER_EMAILS`, the user is a **teacher**.
3. Otherwise, if its local part (before the `@`) starts with **two digits**, such as `27msmith@athenian.org`, the user
   is a **student**.
4. Anyone else is refused at sign-in: *… isn't set up to use History Walk.*

Addresses are compared without regard to case. There is no list of students to keep: every account that matches the
pattern is admitted, which is every student account at the school.

## The teacher list

`TEACHER_EMAILS` holds the teachers' addresses, separated by commas. Spaces around them are ignored, and so is case:

```bash
TEACHER_EMAILS=ateacher@athenian.org, another@athenian.org
```

Each deployment has its own list. A teacher who uses both walks must be on both.

### Adding a teacher

1. Add the address to `TEACHER_EMAILS` on the deployment. The deployment restarts.
2. The teacher signs in. If they were already signed in, their next click gives them teacher access: the server checks
   the role against the list on every request.
3. Ask them to **sign out and in once**. The class pages count a user's moves by the role recorded at their last
   sign-in, so until then their walk can still appear among the students' if they were signed in as a student before.

### Removing a teacher

1. Remove the address from `TEACHER_EMAILS`. The deployment restarts.
2. On their next request, a removed teacher whose address is not a student's is signed out, everywhere, and can't sign
   in again.
3. Their trial walk stays in the database. If they made moves in the student view, those are still counted in the
   class pages: **Clear teachers' trial walks** on the admin page removes counted moves of former teachers too.

### A teacher with a student-pattern address

An address on the teacher list is a teacher, whatever it looks like. Remove it from the list and it becomes a
student, if it starts with two digits.

## What each role can reach

| | Student | Teacher |
|---|---|---|
| The walk | yes | yes; a test walk, left out of the class pages |
| `/summary`, `/reasons` | refused (403) | yes |
| `/admin` and its two buttons | refused (403) | yes |
| `/slides` and `/slide/…` | refused (403) | yes, when `ALLOW_SLIDE_ACCESS=true` |
| `/threaddump` | refused (403) | yes |
| **reset** link | no | when `SHOW_RESET_BUTTON=true`, in the teacher view |

Someone not signed in who opens a teacher page is sent to Google sign-in first. The class pages, the admin page,
sign-in, and sign-out are sent with `Cache-Control: no-store, private`, so neither the browser nor Cloudflare keeps a
copy.

## The student view

The admin page's switch, **Teachers see student view**, makes every teacher's page a student's and counts their moves
like a student's, for trial runs. It is a single setting for the deployment, stored in the database
(`history.settings`), so it survives restarts and applies to every teacher at once. The
[teacher guide](../teachers/student-view.md) describes it from a teacher's side; for an administrator:

- It changes only what teachers see and how their moves are counted. Access does not change: a teacher in the student
  view can still open `/summary`, `/reasons`, and `/admin` by address.
- Each move a teacher makes while it is on is stored with `counted_as_student = true`, and the class pages count it.
  Moves stay counted after the switch goes back, until **Clear teachers' trial walks** deletes them.
- To check or set it without a browser:

    ```sql
    SELECT teachers_see_student_view, updated FROM history.settings;
    UPDATE history.settings SET teachers_see_student_view = false, updated = now() WHERE id = 1;
    ```

## Removing a student's walk

No page deletes a student's walk; do it in the database (see [Database](database.md#deleting-a-users-walk)).

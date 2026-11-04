# Trying the walk as a student

Before you give History Walk to a class, you can see exactly what your students will see, and check that your moves
show up in the class pages the way theirs will. The **admin** page (top right in the teacher view, or `/admin`) has
one switch for this, and a button to clean up afterwards.

## The switch

The admin page says which view teachers see now, and has one button:

| While the page says | The button reads | Pressing it |
|---|---|---|
| *Teachers currently see the teacher view.* | **Teachers see student view** | switches every teacher to the student view |
| *Teachers currently see the student view.* | **Teachers see teacher view** | switches every teacher back |

The switch is **for the whole app**: it changes the view of every teacher on this walk at once, not only yours. It
starts in the teacher view.

!!! note "A button that names what it does"

    The button names the view it switches *to*, not the one you're in. If two teachers both have the admin page open
    and both press **Teachers see student view**, the second press changes nothing: it sets the same view again
    rather than flipping it back.

## In the student view

While the switch is on, every teacher's page is a student's page:

- No *teacher* label, no *not counted* note, and no links to the class pages, admin, or reset; only **logout**.
- **Your moves count.** Each decision you make and explain is counted like a student's: you appear in the
  [class summary](class-summary.md) and your reasons in the [student reasons](student-reasons.md).

The class pages and the admin page still work for you; there is just no link to them. Type the address: the walk's
address followed by `/admin` (or `/summary`, `/reasons`). The admin page's first line, **Back to the walk**, takes you
back to the walk.

!!! warning "Moves you already made aren't counted again"

    A decision you already explained in the teacher view is not asked about again: the app keeps your first reason,
    so making the same move in the student view counts nothing. For a trial run that shows up properly in the class
    pages, **clear the teachers' walks first** (below), so every teacher starts from the first slide.

## Clearing the teachers' walks

**Clear teachers' trial walks**, on the admin page, deletes the walks of **every teacher** on the teacher list:

- every move and reason they made, in either view;
- and it sends each of them back to the first slide.

It also removes any counted move left by someone who has since been taken off the teacher list, so the class pages
end up with no teacher's moves at all. **Students' walks are never touched.**

The browser asks you to confirm before anything is deleted. The admin page shows **Teacher moves counted in the class
pages**, the number of teacher moves the class pages include right now; after a clear it is 0.

Moves made in the student view **stay counted** after you switch back to the teacher view, until you clear. So switch
back and clear before the class starts, and the class pages will show only your students.

## A trial run, start to finish

1. Open **admin**. Press **Clear teachers' trial walks** and confirm, so every teacher starts fresh.
2. Press **Teachers see student view**. Tell your colleagues: they are in the student view too.
3. Go **Back to the walk** and walk it as a student would: make decisions, write reasons, hit a dead end, go back.
4. Open `/summary` and `/reasons` (type the addresses): your walk appears like a student's.
5. Open `/admin` and press **Teachers see teacher view**.
6. Press **Clear teachers' trial walks** again, so the class starts with clean class pages.
7. Give the class the walk's address.

## Your own reset versus the clear

| | **reset** (top right) | **Clear teachers' trial walks** (admin) |
|---|---|---|
| Whose walk | Only yours | Every teacher's |
| Available | Only if your administrator turned it on, and only in the teacher view | Always, on the admin page |
| Asks first | Yes | Yes |
| Also removes counted moves of former teachers | No | Yes |

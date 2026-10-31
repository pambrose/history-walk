# History Walk

## Repos

There are 3 repos involved in this project:

* [history-walk](https://github.com/pambrose/history-walk)
* [history-walk-content](https://github.com/pambrose/history-walk-content)
* [history-walk-slides](https://github.com/pambrose/history-walk-slides)

The *history-walk* repo is duplicated in the client in *history-walk* and *history-walk-moses*. Only the *history-walk*
repo should be updated and the other should only pull from GitHub. Each is used to create
a Heroku app with different env vars One is called *jermain-walk* and the other is *moses-walk*.

The *history-walk-content* repo is used to hold the content for the slides. The content is edited there and then
copied to the *history-walk* repos.

The *history-walk-slides* repo is used to hold the core slide code. It is a dependancy of the other repos.

## Env Vars

### Local only
* SLIDES_LOCAL_FILENAME - The name of the local file to be used for the slides.

### Heroku
* SLIDES_REPO_TYPE
* SLIDES_REPO_OWNER
* SLIDES_REPO_NAME
* SLIDES_REPO_BRANCH
* SLIDES_REPO_PATH
* SLIDES_REPO_FILENAME
* SLIDES_VARIABLE_NAME - The name of the slides variable.
* DISPLAY_CONSECUTIVE_CORRECT_DECISIONS

## Gradle Tasks

### Running

* jsRun - Starts a webpack dev server on port 3000
* jvmRun - Starts a dev server on port 8080

### Packaging

* jsBrowserWebpack - Bundles the compiled js files into `build/dist`
* jsJar - Packages a standalone "web" frontend jar with all required files into `build/libs/*.jar`
* jvmJar - Packages a backend jar with compiled source files into `build/libs/*.jar`
* jar - Packages a "fat" jar with all backend sources and dependencies while also embedding frontend resources
  into `build/libs/*.jar`

## Heroku Deployment

* Add a Procfile
* Add config var `GRADLE_TASK = -Pprod=true jar`
* Add buildpack `heroku/gradle`

To determine the database URL:
```bash
heroku pg:credentials:url DATABASE -a app-name
```

### Pushing to Heroku from a branch

```bash
git push heroku Jan-2026:main
```

### Building the Database

The schema is managed by [Flyway](https://documentation.red-gate.com/flyway). Migrations live in
`src/jvmMain/resources/db/migration` and are applied automatically when the server starts, so every
Heroku deploy brings the database up to date. The initial schema is `V001__create_schema.sql`.

To change the schema, add a new migration with the next version number, e.g. `V002__add_column.sql`.
Never edit a migration that has already been applied: Flyway checksums applied migrations and will
refuse to start if one changes.

### Debug Deployment

* SLIDES_LOCAL_FILENAME=src/jvmMain/kotlin/Slides.kt
* SHOW_RESET_BUTTON=true
* ALLOW_SLIDE_ACCESS=true

There are two endpoints for see the student progress:

* /summary
* /reasons

An overview of the slides for debugging/reviewing is at /slides
(requires ALLOW_SLIDE_ACCESS=true)

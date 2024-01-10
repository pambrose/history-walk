# History Walk

## Repos

There are 3 repos involved in this project:

* [history-walk](https://github.com/pambrose/history-walk)
* [history-walk-slides](https://github.com/pambrose/history-walk-slides)
* [history-walk-content](https://github.com/pambrose/history-walk-content)

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

### Heroku Deployment

* Add a Procfile
* Add config var `GRADLE_TASK = -Pprod=true jar`
* Add buildpack `heroku/gradle`

To determine the database URL:
```bash
heroku pg:credentials:url DATABASE
```

### Debug Deployment

* SLIDES_LOCAL_FILENAME=src/jvmMain/kotlin/Slides.kt
* SHOW_RESET_BUTTON=true
* ALLOW_SLIDE_ACCESS=true

# History Walk

## Repos

There are 3 repos involved in this project:

* [history-walk](https://github.com/pambrose/history-walk)
* [history-walk-slides](https://github.com/pambrose/history-walk-slides)
* [history-walk-content](https://github.com/pambrose/history-walk-content)

## Env Vars

* SLIDES_LOCAL_FILENAME - The name of the local file to be used for the slides.
* SLIDES_REPO_TYPE
* SLIDES_REPO_OWNER
* SLIDES_REPO_NAME
* SLIDES_REPO_BRANCH
* SLIDES_REPO_PATH
* SLIDES_REPO_FILENAME

## Gradle Tasks

### Running

* frontendRun - Starts a webpack dev server on port 3000
* backendRun - Starts a dev server on port 8080

### Packaging

* frontendBrowserWebpack - Bundles the compiled js files into `build/distributions`
* frontendJar - Packages a standalone "web" frontend jar with all required files into `build/libs/*.jar`
* backendJar - Packages a backend jar with compiled source files into `build/libs/*.jar`
* jar - Packages a "fat" jar with all backend sources and dependencies while also embedding frontend resources into `build/libs/*.jar`

### Heroku Deployment

* Add a Procfile
* Add config var `GRADLE_TASK = -Pprod=true jar`
* Add buildpack `heroku/gradle`


## Gradle Tasks

### Resource Processing

* generatePotFile - Generates a `src/frontendMain/resources/i18n/messages.pot` translation template file.

### Compiling

* compileKotlinFrontend - Compiles frontend sources.
* compileKotlinBackend - Compiles backend sources.

### Running

* frontendRun - Starts a webpack dev server on port 3000
* backendRun - Starts a dev server on port 8080

### Packaging

* frontendBrowserWebpack - Bundles the compiled js files into `build/distributions`
* frontendJar - Packages a standalone "web" frontend jar with all required files into `build/libs/*.jar`
* backendJar - Packages a backend jar with compiled source files into `build/libs/*.jar`
* jar - Packages a "fat" jar with all backend sources and dependencies while also embedding frontend resources
  into `build/libs/*.jar`

### Heroku Deployment

* Add a Procfile
* Add config var `GRADLE_TASK = -Pprod=true jar`
* Add buildpack `heroku/gradle`

For more info see [this post](https://github.com/rjaros/kvision/issues/48).
(Ignore suggestion of adding `heroku/nodejs` buildpack.)

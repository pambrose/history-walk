GRADLE         := ./gradlew
GRADLE_VERSION ?= 9.8.2

# Heroku app for the Heroku targets, e.g. `make log APP=history-walk`; omit to use the git remote's default app.
APP ?=
HEROKU_APP_FLAG := $(if $(APP),-a $(APP))

.DEFAULT_GOAL := help

.PHONY: help server client build clean tests versions depends yarn-unlock upgrade-wrapper creds restart log

##@ General

help: ## Show this help
	@awk 'BEGIN { FS = ":.*## "; printf "Usage: make \033[36m<target>\033[0m [APP=<heroku-app>]\n" } \
		/^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } \
		/^[a-zA-Z0-9_-]+:.*## / { printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

##@ Development

server: ## Run the Ktor server on port 8080 with continuous rebuild
	$(GRADLE) jvmRun -t

client: ## Run the webpack dev server on port 3000 (proxies the API to 8080)
	$(GRADLE) jsBrowserDevelopmentRun -t

##@ Build and test

build: clean ## Clean, then build the fat jar (build/libs/history-walk-1.0.0.jar)
	$(GRADLE) jar

clean: ## Remove build outputs
	$(GRADLE) clean

tests: ## Run the JVM tests every time (needs Docker for Testcontainers)
	$(GRADLE) jvmTest --rerun

##@ Dependencies

versions: ## Report dependencies with newer versions
	$(GRADLE) dependencyUpdates

depends: ## Print the dependency tree
	$(GRADLE) dependencies

yarn-unlock: ## Refresh the Kotlin/JS yarn lock file after npm dependency changes
	$(GRADLE) kotlinUpgradeYarnLock

upgrade-wrapper: ## Upgrade the Gradle wrapper (pick the version with GRADLE_VERSION=x.y.z)
	$(GRADLE) wrapper --gradle-version=$(GRADLE_VERSION) --distribution-type=bin

##@ Heroku

creds: ## Print the Heroku database URL
	heroku pg:credentials:url DATABASE $(HEROKU_APP_FLAG)

restart: ## Restart the Heroku dynos
	heroku ps:restart $(HEROKU_APP_FLAG)

log: ## Tail the Heroku logs
	heroku logs --tail $(HEROKU_APP_FLAG)

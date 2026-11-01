GRADLE         := ./gradlew
GRADLE_VERSION ?= 9.8.2

# Heroku app for the Heroku targets, e.g. `make log APP=history-walk`; omit to use the git remote's default app.
APP ?=
HEROKU_APP_FLAG := $(if $(APP),-a $(APP))

# One local Postgres instance holding both databases; override any of these, e.g. `make db DB_PORT=5433`.
DATABASES    := jermain moses
DB_CONTAINER ?= history-walk
DB_IMAGE     ?= postgres:18
DB_USER      ?= postgres
DB_PASSWORD  ?= docker
DB_PORT      ?= 5432
DB_DATA_DIR  ?= $(HOME)/docker/volumes/$(DB_CONTAINER)

.DEFAULT_GOAL := help

.PHONY: help db reset-jermain reset-moses stop-db server client build clean tests versions depends yarn-unlock upgrade-wrapper creds restart log

##@ General

help: ## Show this help
	@awk 'BEGIN { FS = ":.*## "; printf "Usage: make \033[36m<target>\033[0m [APP=<heroku-app>]\n" } \
		/^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } \
		/^[a-zA-Z0-9_-]+:.*## / { printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

##@ Development

db: ## Start the Postgres instance and create the jermain and moses databases if missing
	@if docker container inspect $(DB_CONTAINER) >/dev/null 2>&1; then \
		docker start $(DB_CONTAINER) >/dev/null; \
	else \
		mkdir -p $(DB_DATA_DIR) && \
		docker run -d --name $(DB_CONTAINER) \
			-e POSTGRES_USER=$(DB_USER) -e POSTGRES_PASSWORD=$(DB_PASSWORD) \
			-p 127.0.0.1:$(DB_PORT):5432 -v $(DB_DATA_DIR):/var/lib/postgresql \
			--restart unless-stopped $(DB_IMAGE) >/dev/null; \
	fi
	@# Probe over TCP: the image's first-run init server listens only on the socket, then restarts.
	@until docker exec $(DB_CONTAINER) pg_isready -q -h 127.0.0.1 -U $(DB_USER); do sleep 1; done
	@for db in $(DATABASES); do \
		docker exec $(DB_CONTAINER) psql -U $(DB_USER) -tAc "SELECT 1 FROM pg_database WHERE datname = '$$db'" | grep -qx 1 \
			|| docker exec $(DB_CONTAINER) createdb -U $(DB_USER) $$db || exit 1; \
		echo "Postgres ready: jdbc:pgsql://localhost:$(DB_PORT)/$$db (user $(DB_USER))"; \
	done

reset-jermain: ## Drop and recreate the jermain database (its data is lost; moses is untouched)
reset-moses: ## Drop and recreate the moses database (its data is lost; jermain is untouched)

# $* is the database name; the db prerequisite makes sure the instance is up and both databases exist.
reset-jermain reset-moses: reset-%: db
	@docker exec $(DB_CONTAINER) dropdb -U $(DB_USER) --force $*
	@docker exec $(DB_CONTAINER) createdb -U $(DB_USER) $*
	@echo "Reset $*: the database is empty; Flyway rebuilds the schema on the next server start"

stop-db: ## Stop the Postgres instance, and with it both databases (the data is kept)
	@if docker container inspect $(DB_CONTAINER) >/dev/null 2>&1; then \
		docker stop $(DB_CONTAINER) >/dev/null && echo "Stopped $(DB_CONTAINER); make db starts it again"; \
	else \
		echo "No $(DB_CONTAINER) container to stop"; \
	fi

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

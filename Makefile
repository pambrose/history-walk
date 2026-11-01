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

# Docker image of the server; `make docker-run` serves it on DOCKER_PORT against the DOCKER_DB database from `make db`.
VERSION      := $(shell sed -n 's/^version[[:space:]]*=[[:space:]]*//p' gradle.properties)
DOCKER_IMAGE ?= history-walk
DOCKER_PORT  ?= 8080
DOCKER_DB    ?= jermain
ENV_FILE     ?= .env

# The addresses `make served-version` compares: the app as Cloudflare serves it, and the Heroku app behind it, e.g.
# make served-version PUBLIC_URL=https://walk.example.org ORIGIN_URL=https://<app>.herokuapp.com
PUBLIC_URL   ?=
ORIGIN_URL   ?=

.DEFAULT_GOAL := help

.PHONY: help db reset-jermain reset-moses stop-db server client build clean tests coverage lint format zizmor ci docker-build docker-run docker-smoke versions depends yarn-unlock upgrade-wrapper creds restart log served-version

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

build: clean ## Clean, then build the fat jar (build/libs/history-walk-VERSION.jar)
	$(GRADLE) jar

clean: ## Remove build outputs
	$(GRADLE) clean

tests: ## Run the JVM tests every time (needs Docker for Testcontainers)
	$(GRADLE) jvmTest --rerun

coverage: ## Run the JVM tests and write the Kover reports (build/reports/kover/html/index.html)
	$(GRADLE) jvmTest --rerun koverHtmlReport koverXmlReport koverLog

lint: ## Check the Kotlin sources with kotlinter (generated sources are skipped)
	$(GRADLE) lintKotlin

format: ## Reformat the Kotlin sources with kotlinter, fixing what lint can fix by itself
	$(GRADLE) formatKotlin

# The online audits (a pinned SHA that is not a commit of its action, known-vulnerable actions) need a GitHub token,
# taken from GH_TOKEN or the gh CLI; without one zizmor runs only its offline audits.
zizmor: ## Audit the GitHub Actions workflows and dependabot.yml with zizmor, as the zizmor workflow does
	@GH_TOKEN="$${GH_TOKEN:-$$(gh auth token 2>/dev/null)}"; \
	if [ -n "$$GH_TOKEN" ]; then export GH_TOKEN; else unset GH_TOKEN; fi; \
	zizmor .

# Every gate CI runs, in one command, so this is the check before merging: lint, the zizmor audit, the tests with
# coverage (their results not taken from the build cache), and the Docker image, built and then started against
# Postgres. Needs Docker and zizmor.
ci: ## Run every CI gate locally: lint, zizmor, the tests with coverage, and the Docker image build and smoke test
	$(MAKE) lint
	$(MAKE) zizmor
	$(GRADLE) --no-build-cache cleanJvmTest jvmTest koverXmlReport koverHtmlReport koverLog
	$(MAKE) docker-build
	$(MAKE) docker-smoke

##@ Docker

docker-build: ## Build the fat jar, then an image holding it (history-walk:VERSION and :latest)
	$(GRADLE) jar
	docker build --build-arg APP_VERSION=$(VERSION) -t $(DOCKER_IMAGE):$(VERSION) -t $(DOCKER_IMAGE):latest .

# ENV_FILE is read as the shell reads it (so quoted values lose their quotes), then each of its variables is passed to the
# container. Inside the container localhost is the container itself, so the database is reached through
# host.docker.internal, and Google must send the browser back to the container's port. Start the database with make db.
docker-run: docker-build ## Run the image on DOCKER_PORT with ENV_FILE (.env), against the DOCKER_DB database (jermain)
	@[ -f "$(ENV_FILE)" ] || { echo "$(ENV_FILE) not found: it supplies GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, and the rest"; exit 1; }
	@set -a && . "$(abspath $(ENV_FILE))" && set +a && \
		export DBMS_URL="jdbc:pgsql://host.docker.internal:$(DB_PORT)/$(DOCKER_DB)" && \
		export OAUTH_CALLBACK_URL="http://localhost:$(DOCKER_PORT)/auth/callback" && \
		docker run --rm --name $(DOCKER_IMAGE)-server -p 127.0.0.1:$(DOCKER_PORT):8080 \
			--add-host=host.docker.internal:host-gateway \
			$$(sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)=.*/-e \1/p' "$(ENV_FILE)") -e DBMS_URL -e OAUTH_CALLBACK_URL \
			$(DOCKER_IMAGE):$(VERSION)

# Starts the image against a throwaway Postgres on a Docker network of its own and checks that it migrates the empty
# database and answers: /ping, /version, the page shell's Cache-Control, the bundle, and a 401 for an RPC call without
# a session. Sign-in needs real Google credentials only when someone signs in, so placeholders let the server start, and
# a loopback callback URL means no SESSION_SECRET is required. Everything it starts is removed, pass or fail; on a
# failure it prints the end of the server's log. The Docker Image workflow runs it too.
SMOKE_NAME ?= history-walk-smoke
SMOKE_PORT ?= 18080
docker-smoke: ## Start the image (make docker-build first) against a throwaway Postgres and check that it answers
	@set -e; \
	cleanup() { docker rm -f $(SMOKE_NAME)-app $(SMOKE_NAME)-db >/dev/null 2>&1 || true; \
		docker network rm $(SMOKE_NAME) >/dev/null 2>&1 || true; }; \
	fail() { echo "docker-smoke: $$1" >&2; docker logs $(SMOKE_NAME)-app 2>&1 | tail -40 >&2 || true; cleanup; exit 1; }; \
	cleanup; \
	docker network create $(SMOKE_NAME) >/dev/null; \
	docker run -d --name $(SMOKE_NAME)-db --network $(SMOKE_NAME) \
		-e POSTGRES_PASSWORD=smoke -e POSTGRES_DB=historywalk $(DB_IMAGE) >/dev/null; \
	ready=; for _ in $$(seq 1 60); do \
		if docker exec $(SMOKE_NAME)-db pg_isready -q -h 127.0.0.1 -U postgres; then ready=1; break; fi; sleep 1; \
	done; \
	[ -n "$$ready" ] || fail "Postgres did not start"; \
	docker run -d --name $(SMOKE_NAME)-app --network $(SMOKE_NAME) -p 127.0.0.1:$(SMOKE_PORT):8080 \
		-e DBMS_URL=jdbc:pgsql://$(SMOKE_NAME)-db:5432/historywalk -e DBMS_USERNAME=postgres -e DBMS_PASSWORD=smoke \
		-e GOOGLE_CLIENT_ID=smoke -e GOOGLE_CLIENT_SECRET=smoke \
		-e OAUTH_CALLBACK_URL=http://localhost:8080/auth/callback \
		$(DOCKER_IMAGE):$(VERSION) >/dev/null; \
	url=http://127.0.0.1:$(SMOKE_PORT); up=; \
	for _ in $$(seq 1 60); do \
		if curl -fsS -o /dev/null $$url/ping 2>/dev/null; then up=1; break; fi; \
		[ "$$(docker inspect -f '{{.State.Running}}' $(SMOKE_NAME)-app)" = true ] || break; \
		sleep 2; \
	done; \
	[ -n "$$up" ] || fail "the server did not answer /ping"; \
	curl -fsS $$url/version | grep -q "$(VERSION)" || fail "/version does not report $(VERSION)"; \
	curl -fsS -D - -o /dev/null $$url/ | grep -qi '^cache-control: no-cache, private' \
		|| fail "the page shell is not sent with Cache-Control: no-cache, private"; \
	curl -fsS -o /dev/null $$url/main.bundle.js || fail "main.bundle.js is not served"; \
	status=$$(curl -s -o /dev/null -w '%{http_code}' -X POST $$url/rpc/routeContentServiceManager0); \
	[ "$$status" = 401 ] || fail "an RPC call without a session got $$status, not 401"; \
	cleanup; \
	echo "docker-smoke: $(DOCKER_IMAGE):$(VERSION) migrated an empty database and answered every check"

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

# Shows the client bundle's version as Heroku (ORIGIN_URL) serves it and as Cloudflare (PUBLIC_URL) serves it, with
# Cloudflare's cf-cache-status. The version is the line App.start() logs, "History Walk version VERSION - release date".
# It fails when Cloudflare serves another bundle than Heroku, or caches the bundle, which a deploy then does not
# replace: purge PUBLIC_URL/main.bundle.js in Cloudflare (see AGENTS.md). It fetches with GET, not HEAD.
BUNDLE_VERSION_RE = History Walk version [0-9]+\.[0-9]+\.[0-9]+ - [^"]+
served-version: ## Show the bundle version Heroku and Cloudflare serve; fails if they differ or Cloudflare caches it
	@[ -n "$(PUBLIC_URL)" ] || { echo "Set PUBLIC_URL (and ORIGIN_URL), e.g. make served-version PUBLIC_URL=https://walk.example.org ORIGIN_URL=https://<app>.herokuapp.com" >&2; exit 1; }
	@problems=0; problem() { echo "  - $$1" >&2; problems=$$((problems + 1)); }; \
	served() { curl -sS --compressed -m 30 -D - "$$1/main.bundle.js" \
		| LC_ALL=C grep -aoiE '^cf-cache-status: [a-z]+|$(BUNDLE_VERSION_RE)'; }; \
	version() { grep -iv '^cf-cache-status' | head -1; }; \
	echo "This checkout:       $(VERSION)"; \
	if [ -n "$(ORIGIN_URL)" ]; then \
		origin=$$(served "$(ORIGIN_URL)" | version); \
		echo "Heroku's bundle:     $${origin:-not found}"; \
	else origin=; echo "Heroku's bundle:     (set ORIGIN_URL to compare)"; fi; \
	fetched=$$(served "$(PUBLIC_URL)"); \
	public=$$(echo "$$fetched" | version); \
	cached=$$(echo "$$fetched" | sed -n 's/^[Cc][Ff]-[Cc][Aa][Cc][Hh][Ee]-[Ss][Tt][Aa][Tt][Uu][Ss]: //p'); \
	echo "Cloudflare's bundle: $${public:-not found} (cf-cache-status: $${cached:-none})"; \
	[ -n "$$public" ] || problem "found no version in $(PUBLIC_URL)/main.bundle.js"; \
	[ -z "$$origin" ] || [ -z "$$public" ] || [ "$$origin" = "$$public" ] \
		|| problem "Cloudflare serves another bundle than Heroku: purge $(PUBLIC_URL)/main.bundle.js in Cloudflare"; \
	case "$$cached" in ""|BYPASS|DYNAMIC) ;; \
		*) problem "Cloudflare caches the bundle ($$cached), so a deploy does not replace its copy: see AGENTS.md";; \
	esac; \
	[ $$problems -eq 0 ] || exit 1

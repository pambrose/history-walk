GRADLE         := ./gradlew
GRADLE_VERSION ?= 9.9.1

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

# The Docker Hub repository `make docker-push` publishes the image to (docker login first), and the platforms it builds.
DOCKER_REPO      ?= pambrose/history-walk
DOCKER_PLATFORMS ?= linux/amd64,linux/arm64

# `make release` publishes only from a clean checkout of this branch that matches origin, and whose CI on GitHub passed:
# each of the RELEASE_CHECKS jobs (the Tests, Lint, Docker Image, and Security workflows) must have run and passed, so a
# release started before CI has queued them all stops rather than passing on the ones that ran. image-scan and lock-scan
# (the image, and the client's npm lock) are the gates make ci can't run, since they need Trivy, so this is where a
# release waits for them.
RELEASE_BRANCH ?= master
RELEASE_CHECKS := test lint docker-image image-scan lock-scan
# VERSION as a regular expression, for finding its sections in CHANGELOG.md and RELEASE_NOTES.md
VERSION_RE   = $(subst .,\.,$(VERSION))
# Where make release writes VERSION's section of RELEASE_NOTES.md, the GitHub release's notes
RELEASE_NOTES_FILE = build/release-notes-$(VERSION).md

# The addresses `make served-version` reads: the app as its users reach it, and, for one behind Cloudflare, the
# deployment behind it (the origin), e.g. make served-version PUBLIC_URL=https://walk.example.org
# ORIGIN_URL=https://<service>.up.railway.app. Without ORIGIN_URL it shows only PUBLIC_URL's bundle.
PUBLIC_URL   ?=
ORIGIN_URL   ?=

# The Railway project that runs the walks (railway list --json shows its ID), and the walks' services in it, each running
# the Docker Hub image pambrose/history-walk; RAILWAY_URLS are their public addresses, checked after a restart.
RAILWAY_PROJECT     ?= a0c8cc4e-4057-4ddc-a7a8-881ccfa721e6
RAILWAY_ENVIRONMENT ?= production
RAILWAY_SERVICES    ?= Jermain Moses
RAILWAY_URLS        ?= https://jermain.up.railway.app https://moses.up.railway.app
# make log: how many recent lines of each walk's log it shows, and SERVICE=Jermain or SERVICE=Moses for one walk;
# make log-jermain and make log-moses: how many lines they show before following the log
LOG_LINES           ?= 100
SERVICE             ?=
TAIL_LINES          ?= 10

# The documentation site, a Zensical project in WEBSITE_DIR. Zensical is a dependency of the Python project in website/,
# which each website target first syncs into website/.venv with uv. WEBSITE_ADDR=0.0.0.0:8000 serves it to the network.
WEBSITE_DIR  := website/history-walk
WEBSITE_ADDR ?= localhost:8000
ZENSICAL     := $(abspath website/.venv/bin/zensical)

.DEFAULT_GOAL := help

.PHONY: help db reset-jermain reset-moses stop-db server client build clean tests js-tests coverage lint \
		format zizmor ci docker-build docker-push _docker-push docker-run docker-smoke release-check release \
		_confirm-release release-github railway-restart railway-redeploy log log-jermain log-moses _follow-log website-env \
		website-serve website-build website-clean upgrade-site versions versions-all depends yarn-unlock upgrade-wrapper \
		railway-versions served-version

##@ General

help: ## Show this help
	@awk 'BEGIN { FS = ":.*## "; printf "Usage: make \033[36m<target>\033[0m\n" } \
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
	@# Probe over TCP: the image's first-run init server listens only on the socket, then restarts. A container that
	@# never gets there (one crash-looping under --restart unless-stopped, say, on data from another Postgres major in
	@# DB_DATA_DIR) is given up on after a minute, with the end of its log.
	@ready=; for _ in $$(seq 1 60); do \
		if docker exec $(DB_CONTAINER) pg_isready -q -h 127.0.0.1 -U $(DB_USER) 2>/dev/null; then ready=1; break; fi; \
		sleep 1; \
	done; \
	[ -n "$$ready" ] || { echo "make db: Postgres in $(DB_CONTAINER) was not ready within a minute; its log ends:" >&2; \
		docker logs --tail 30 $(DB_CONTAINER) >&2; exit 1; }
	@for db in $(DATABASES); do \
		docker exec $(DB_CONTAINER) psql -U $(DB_USER) -tAc "SELECT 1 FROM pg_database WHERE datname = '$$db'" | grep -qx 1 \
			|| docker exec $(DB_CONTAINER) createdb -U $(DB_USER) $$db || exit 1; \
		echo "Postgres ready: jdbc:postgresql://localhost:$(DB_PORT)/$$db (user $(DB_USER))"; \
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

build: clean ## Clean, then build the fat jar (build/libs/history-walk.jar, the file deployments run)
	$(GRADLE) jar

clean: ## Remove build outputs
	$(GRADLE) clean

# The client specs (src/jsTest, plus src/commonTest, whose specs run on both targets) run in jsBrowserTest: Kotest under
# Karma, in headless Chrome, which must be installed. jsTest runs the same task, then collects its report.
tests: ## Run the JVM and client tests every time (the JVM tests need Docker for Testcontainers)
	$(GRADLE) jvmTest --rerun jsBrowserTest --rerun

js-tests: ## Run only the client tests every time (Kotest in headless Chrome; no Docker needed)
	$(GRADLE) jsBrowserTest --rerun

# koverVerify comes last, so the reports are written even when the coverage is under the floor (build.gradle.kts).
coverage: ## Run the JVM and client tests, write the JVM code's Kover reports, and check the coverage floor
	$(GRADLE) jvmTest --rerun jsBrowserTest --rerun koverHtmlReport koverXmlReport koverLog koverVerify

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

# Every gate CI runs but the Trivy scans (security.yml's image-scan and lock-scan, which need Trivy), in one command, so
# this is the check before merging: lint, the zizmor audit, the docs site's strict build, the JVM tests with coverage
# (checked against the floor in build.gradle.kts) and the client tests (their results not taken from the build cache),
# the Docker image build, and the image started against Postgres. Needs Docker, Chrome, zizmor, and uv.
ci: ## Run every CI gate locally but the Trivy scans: lint, zizmor, the docs site, the tests, the image and its smoke test
	$(MAKE) lint
	$(MAKE) zizmor
	$(MAKE) website-build
	$(GRADLE) --no-build-cache cleanJvmTest cleanJsBrowserTest jvmTest jsBrowserTest koverXmlReport koverHtmlReport koverLog \
		koverVerify
	$(MAKE) docker-build
	$(MAKE) docker-smoke

##@ Docker

# APP_VERSION and APP_REVISION only label the image (org.opencontainers.image.version and .revision, the commit it was
# built from): the jar the Dockerfile copies has no version in its name.
APP_REVISION = $(shell git rev-parse HEAD 2>/dev/null)
docker-build: ## Build the fat jar, then an image holding it (history-walk:VERSION and :latest)
	$(GRADLE) jar
	docker build --build-arg APP_VERSION=$(VERSION) --build-arg APP_REVISION=$(APP_REVISION) \
		-t $(DOCKER_IMAGE):$(VERSION) -t $(DOCKER_IMAGE):latest .

# One jar serves every platform, and the base image is pinned to its multi-arch index, so buildx builds the image for
# each of DOCKER_PLATFORMS from the same jar and pushes them under one tag. The image holds no secrets (they come from
# the environment at startup), and a repository the first push creates takes the account's default visibility. It asks
# before publishing, and a push replaces what Docker Hub held under both tags, so bump the version in gradle.properties
# first to keep the previous release's image.
docker-push: ## Build the fat jar, then build and push a multi-arch image to Docker Hub (DOCKER_REPO:VERSION and :latest)
	@echo "About to publish to Docker Hub for $(DOCKER_PLATFORMS):"
	@echo "  $(DOCKER_REPO):$(VERSION)"
	@echo "  $(DOCKER_REPO):latest"
	@printf 'Continue? [y/N] ' && read ans && [ "$$ans" = "y" ]
	$(GRADLE) jar
	@$(MAKE) --no-print-directory _docker-push

# The push itself, of the jar build/libs holds, with no prompt: docker-push builds the jar and asks first, and make
# release pushes the jar make ci built and the smoke test ran, rather than a rebuild (asking for jar stamps a new build
# time, so the build would compile and webpack again).
_docker-push:
	@[ -f build/libs/history-walk.jar ] || { echo "_docker-push: build/libs/history-walk.jar is missing" >&2; exit 1; }
	docker buildx build --build-arg APP_VERSION=$(VERSION) --build-arg APP_REVISION=$(APP_REVISION) \
		--platform $(DOCKER_PLATFORMS) -t $(DOCKER_REPO):$(VERSION) -t $(DOCKER_REPO):latest --push .

# ENV_FILE is read as the shell reads it (so quoted values lose their quotes), then each of its variables is passed to the
# container. Inside the container localhost is the container itself, so the database is reached through
# host.docker.internal, and Google must send the browser back to the container's port. Start the database with make db.
docker-run: docker-build ## Run the image on DOCKER_PORT with ENV_FILE (.env), against the DOCKER_DB database (jermain)
	@[ -f "$(ENV_FILE)" ] || { echo "$(ENV_FILE) not found: it supplies GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, and the rest"; exit 1; }
	@set -a && . "$(abspath $(ENV_FILE))" && set +a && \
		export DBMS_URL="jdbc:postgresql://host.docker.internal:$(DB_PORT)/$(DOCKER_DB)" && \
		export OAUTH_CALLBACK_URL="http://localhost:$(DOCKER_PORT)/auth/callback" && \
		docker run --rm --name $(DOCKER_IMAGE)-server -p 127.0.0.1:$(DOCKER_PORT):8080 \
			--add-host=host.docker.internal:host-gateway \
			$$(sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)=.*/-e \1/p' "$(ENV_FILE)") -e DBMS_URL -e OAUTH_CALLBACK_URL \
			$(DOCKER_IMAGE):$(VERSION)

# Starts the image against a throwaway Postgres on a Docker network of its own and checks that it migrates the empty
# database and answers: /ping, /version, /health (the database reached), the page shell's Cache-Control and security
# headers, the bundle, and a 401 for an RPC call without a session. Sign-in needs real Google credentials only when
# someone signs in, so placeholders let the server start, and a loopback callback URL means no SESSION_SECRET is
# required. Everything it starts is removed however it ends (a trap on exit), and a failure says which check failed and
# prints the end of the server's log. The Docker Image workflow runs it too.
SMOKE_NAME ?= history-walk-smoke
SMOKE_PORT ?= 18080
docker-smoke: ## Start the image (make docker-build first) against a throwaway Postgres and check that it answers
	@set -e; \
	cleanup() { docker rm -f $(SMOKE_NAME)-app $(SMOKE_NAME)-db >/dev/null 2>&1 || true; \
		docker network rm $(SMOKE_NAME) >/dev/null 2>&1 || true; }; \
	fail() { echo "docker-smoke: $$1" >&2; docker logs $(SMOKE_NAME)-app 2>&1 | tail -40 >&2 || true; exit 1; }; \
	cleanup; \
	trap cleanup EXIT; \
	docker network create $(SMOKE_NAME) >/dev/null || fail "could not create the Docker network $(SMOKE_NAME)"; \
	docker run -d --name $(SMOKE_NAME)-db --network $(SMOKE_NAME) \
		-e POSTGRES_PASSWORD=smoke -e POSTGRES_DB=historywalk $(DB_IMAGE) >/dev/null \
		|| fail "could not start Postgres ($(DB_IMAGE))"; \
	ready=; for _ in $$(seq 1 60); do \
		if docker exec $(SMOKE_NAME)-db pg_isready -q -h 127.0.0.1 -U postgres; then ready=1; break; fi; sleep 1; \
	done; \
	[ -n "$$ready" ] || fail "Postgres did not start"; \
	docker run -d --name $(SMOKE_NAME)-app --network $(SMOKE_NAME) -p 127.0.0.1:$(SMOKE_PORT):8080 \
		-e DBMS_URL=jdbc:postgresql://$(SMOKE_NAME)-db:5432/historywalk -e DBMS_USERNAME=postgres -e DBMS_PASSWORD=smoke \
		-e GOOGLE_CLIENT_ID=smoke -e GOOGLE_CLIENT_SECRET=smoke \
		-e OAUTH_CALLBACK_URL=http://localhost:8080/auth/callback \
		$(DOCKER_IMAGE):$(VERSION) >/dev/null || fail "could not start $(DOCKER_IMAGE):$(VERSION); make docker-build first"; \
	url=http://127.0.0.1:$(SMOKE_PORT); up=; \
	for _ in $$(seq 1 60); do \
		if curl -fsS -o /dev/null $$url/ping 2>/dev/null; then up=1; break; fi; \
		[ "$$(docker inspect -f '{{.State.Running}}' $(SMOKE_NAME)-app)" = true ] || break; \
		sleep 2; \
	done; \
	[ -n "$$up" ] || fail "the server did not answer /ping"; \
	curl -fsS $$url/version | grep -q "$(VERSION)" || fail "/version does not report $(VERSION)"; \
	[ "$$(curl -fsS $$url/health || true)" = ok ] || fail "/health does not report the database answering"; \
	curl -fsS -D - -o /dev/null $$url/ | grep -qi '^cache-control: no-cache, private' \
		|| fail "the page shell is not sent with Cache-Control: no-cache, private"; \
	curl -fsS -D - -o /dev/null $$url/ | grep -qi '^x-content-type-options: nosniff' \
		|| fail "the page shell is not sent with X-Content-Type-Options: nosniff"; \
	curl -fsS -o /dev/null $$url/main.bundle.js || fail "main.bundle.js is not served"; \
	status=$$(curl -s -o /dev/null -w '%{http_code}' -X POST $$url/rpc/routeContentServiceManager0 || true); \
	[ "$$status" = 401 ] || fail "an RPC call without a session got $$status, not 401"; \
	echo "docker-smoke: $(DOCKER_IMAGE):$(VERSION) migrated an empty database and answered every check"

##@ Release

# Publishes nothing. It fails unless this checkout is fit to release VERSION, and lists every reason rather than stopping
# at the first, so one run shows all that is left to do: the local checks first, then those that ask GitHub and Docker
# Hub. RELEASE_NOTES.md's "## History Walk VERSION" section becomes the GitHub release's notes, so it must be there and
# end with the **Full Changelog** link. A tag on Docker Hub can be pushed over, so a VERSION already there is refused.
# The check runs named Dependabot are left out: they are Dependabot's own update jobs, which attach to master's commits
# but are not CI, and fail for reasons the commit does not cause (it cannot update .kotlin-js-store/yarn.lock, which has
# no package.json beside it; Kotlin writes that at build time).
release-check: ## Fail, listing every reason, unless this checkout is fit to release VERSION
	@problems=0; problem() { echo "  - $$1" >&2; problems=$$((problems + 1)); }; \
	echo "Checking whether $(VERSION) can be released from $$(git rev-parse --short HEAD)"; \
	[ -n "$(VERSION)" ] || problem "found no version in gradle.properties"; \
	case "$(VERSION)" in *SNAPSHOT*|*-rc*|*-beta*|*-alpha*) \
		problem "$(VERSION) is a pre-release, and make release pushes it as :latest";; esac; \
	[ -z "$$(git status --porcelain)" ] || problem "the working tree is dirty: commit or stash first"; \
	branch=$$(git rev-parse --abbrev-ref HEAD); \
	[ "$$branch" = "$(RELEASE_BRANCH)" ] || problem "releases are cut from $(RELEASE_BRANCH), not $$branch"; \
	if git fetch -q origin $(RELEASE_BRANCH); then \
		[ "$$(git rev-parse HEAD)" = "$$(git rev-parse origin/$(RELEASE_BRANCH))" ] \
			|| problem "HEAD is not origin/$(RELEASE_BRANCH): pull or push first"; \
	else problem "could not fetch origin/$(RELEASE_BRANCH)"; fi; \
	grep -q '^## \[$(VERSION_RE)\]' CHANGELOG.md || problem "CHANGELOG.md has no [$(VERSION)] section"; \
	notes=$$(awk '/^## History Walk $(VERSION_RE)( |$$)/ { f = 1; next } /^## / { f = 0 } f' RELEASE_NOTES.md); \
	if [ -z "$$notes" ]; then problem "RELEASE_NOTES.md has no \"## History Walk $(VERSION)\" section"; \
	else echo "$$notes" | grep -q '\*\*Full Changelog\*\*' \
		|| problem "RELEASE_NOTES.md's $(VERSION) section has no **Full Changelog** link"; fi; \
	if tags=$$(git ls-remote origin "refs/tags/$(VERSION)" "refs/tags/$(VERSION)^{}"); then \
		tagged=$$(echo "$$tags" | sort -k2 | tail -1 | cut -f1); \
		[ -z "$$tagged" ] || [ "$$tagged" = "$$(git rev-parse HEAD)" ] \
			|| problem "tag $(VERSION) on origin points at another commit"; \
	else problem "could not read the tags on origin"; fi; \
	! gh release view "$(VERSION)" >/dev/null 2>&1 || problem "GitHub already has a release $(VERSION)"; \
	docker info >/dev/null 2>&1 || problem "Docker is not running: make ci and the push need it"; \
	if runs=$$(gh api "repos/{owner}/{repo}/commits/$$(git rev-parse HEAD)/check-runs" --paginate \
			--jq '.check_runs[] | select(.name != "Dependabot") | "\(.status) \(.conclusion) \(.name)"' 2>/dev/null); then \
		failing=$$(echo "$$runs" | grep -Ev '^completed (success|skipped|neutral) ' | grep . | tr '\n' ';'); \
		[ -z "$$failing" ] || problem "CI checks have not passed for HEAD: $$failing"; \
		! echo "$$runs" | grep -q '^completed neutral GitGuardian' \
			|| problem "GitGuardian found secrets in HEAD (its neutral result): read its check run"; \
		for c in $(RELEASE_CHECKS); do \
			echo "$$runs" | cut -d' ' -f3- | grep -qxF "$$c" || problem "CI has not run $$c for HEAD"; \
		done; \
	else problem "could not read HEAD's CI checks from GitHub: is HEAD pushed, and gh signed in?"; fi; \
	if out=$$(docker buildx imagetools inspect $(DOCKER_REPO):$(VERSION) 2>&1); then \
		problem "$(DOCKER_REPO):$(VERSION) is on Docker Hub: bump the version, or finish its release: make release-github"; \
	elif ! echo "$$out" | grep -q 'not found'; then \
		problem "could not ask Docker Hub for $(DOCKER_REPO):$(VERSION): $$(echo "$$out" | tail -1)"; \
	fi; \
	if [ $$problems -gt 0 ]; then echo "ERROR: $(VERSION) is not ready to release" >&2; exit 1; fi; \
	echo "Ready to release $(VERSION) from $$(git rev-parse --short HEAD)"

# The last word before anything is published. It refuses if the checkout changed while make ci ran, since an edit or a
# commit made then would be released untested: RELEASE_SHA is the commit make release started from.
_confirm-release:
	@[ -n "$(RELEASE_SHA)" ] || { echo "ERROR: make release runs this, setting RELEASE_SHA" >&2; exit 1; }
	@[ "$$(git rev-parse HEAD)" = "$(RELEASE_SHA)" ] && [ -z "$$(git status --porcelain)" ] \
		|| { echo "ERROR: the checkout changed while the release ran; nothing published" >&2; exit 1; }
	@echo "About to release $(VERSION) from $$(git rev-parse --short HEAD):"
	@echo "  - push $(DOCKER_REPO):$(VERSION) and :latest to Docker Hub, for $(DOCKER_PLATFORMS)"
	@echo "  - create the GitHub release v$(VERSION), tagged $(VERSION), with RELEASE_NOTES.md's $(VERSION) section"
	@printf 'Continue? [y/N] '; read -r answer || answer=; \
	case "$$answer" in [yY]|[yY][eE][sS]) ;; *) echo "Nothing published."; exit 1 ;; esac

# Each step runs only if the one before it passed, so nothing is published until the checkout passes release-check,
# every CI gate (make ci, after a clean build) and the prompt. The image goes first, built from the jar make ci built and
# smoke-tested: if its push fails, no release or tag is created. If the GitHub release then fails (gh signed out, the
# network), the image is on Docker Hub, which a second make release refuses; make release-github finishes it.
release: RELEASE_SHA := $(shell git rev-parse HEAD 2>/dev/null)
release: ## Check, run every CI gate, then push VERSION's image and create its GitHub release (asks first)
	$(MAKE) release-check
	$(MAKE) clean
	$(MAKE) ci
	@$(MAKE) --no-print-directory _confirm-release RELEASE_SHA=$(RELEASE_SHA)
	$(MAKE) _docker-push APP_REVISION=$(RELEASE_SHA)
	@$(MAKE) --no-print-directory release-github RELEASE_SHA=$(RELEASE_SHA)

# Creates VERSION's GitHub release, following the house rules: tag VERSION at RELEASE_SHA (HEAD, unless make release
# passes the commit it started from), title vVERSION, and RELEASE_NOTES.md's section for VERSION as its notes. make
# release runs it last; run it alone to finish a release that stopped after pushing its image. It first checks that
# Docker Hub's DOCKER_REPO:VERSION was built from RELEASE_SHA (the image's org.opencontainers.image.revision label), so
# the tag lands on the commit the image holds. What stays by hand is printed at the end.
release-github: RELEASE_SHA ?= $(shell git rev-parse HEAD 2>/dev/null)
release-github: ## Create VERSION's GitHub release, e.g. to finish a make release that stopped after pushing the image
	@! gh release view "$(VERSION)" >/dev/null 2>&1 \
		|| { echo "release-github: GitHub already has a release $(VERSION)" >&2; exit 1; }
	@built=$$(docker buildx imagetools inspect $(DOCKER_REPO):$(VERSION) \
		--format '{{ index (index .Image "linux/amd64").Config.Labels "org.opencontainers.image.revision" }}' 2>/dev/null); \
	[ -n "$$built" ] || { echo "release-github: $(DOCKER_REPO):$(VERSION) is not on Docker Hub, or says no revision:" \
		"run make release" >&2; exit 1; }; \
	[ "$$built" = "$(RELEASE_SHA)" ] || { echo "release-github: $(DOCKER_REPO):$(VERSION) was built from $$built, not" \
		"$(RELEASE_SHA)" >&2; exit 1; }
	@mkdir -p $(dir $(RELEASE_NOTES_FILE))
	@awk '/^## History Walk $(VERSION_RE)( |$$)/ { f = 1; next } /^## / { f = 0 } f' RELEASE_NOTES.md \
		> $(RELEASE_NOTES_FILE)
	@[ -s $(RELEASE_NOTES_FILE) ] \
		|| { echo "release-github: RELEASE_NOTES.md has no \"## History Walk $(VERSION)\" section" >&2; exit 1; }
	gh release create $(VERSION) --target $(RELEASE_SHA) --title v$(VERSION) --notes-file $(RELEASE_NOTES_FILE) \
		|| { echo "release-github: the image is on Docker Hub; once that is fixed, finish with make release-github" >&2; \
			exit 1; }
	@echo; echo "Released $(VERSION): https://github.com/pambrose/history-walk/releases/tag/$(VERSION)"; \
	echo "Still by hand:"; \
	echo "  1. Redeploy each walk from the new image: make railway-redeploy (it waits until each reports $(VERSION))."; \
	echo "  2. Behind Cloudflare: make served-version PUBLIC_URL=... ORIGIN_URL=..."

##@ Website

# Installs Zensical, at the version uv.lock pins, into website/.venv. Only the dependencies: website/ is not a package
# the docs need built. --locked fails, rather than quietly re-locking, when uv.lock no longer matches pyproject.toml, so
# the site CI publishes is built with the versions the lock pins; make upgrade-site is how the lock changes.
website-env:
	@uv sync --project website --no-install-project --locked --quiet

website-serve: website-env ## Serve the documentation site at WEBSITE_ADDR (localhost:8000), rebuilding it on every save
	cd $(WEBSITE_DIR) && $(ZENSICAL) serve --dev-addr $(WEBSITE_ADDR)

# --strict stops the build at the first warning, such as a link to a page that does not exist
website-build: website-env ## Build the documentation site into website/history-walk/site, failing on any warning
	cd $(WEBSITE_DIR) && $(ZENSICAL) build --clean --strict

website-clean: ## Remove the built documentation site and Zensical's cache
	rm -rf $(WEBSITE_DIR)/site $(WEBSITE_DIR)/.cache

# Moves every package in website/uv.lock (Zensical and what it depends on) to its latest release that pyproject.toml
# allows, then installs them. Run make website-build afterwards to check that the site still builds cleanly.
upgrade-site: ## Upgrade Zensical and its dependencies in website/uv.lock to their latest releases, and install them
	@before=$$($(ZENSICAL) --version 2>/dev/null || echo "not installed"); \
	uv lock --project website --upgrade && \
	uv sync --project website --no-install-project --quiet && \
	echo "Zensical: $$before -> $$($(ZENSICAL) --version)"

##@ Dependencies

# dependencyUpdates is marked incompatible with the configuration cache (build.gradle.kts), so a run with the cache on
# builds an entry only to discard it, and says so. --no-configuration-cache skips the cache for this run instead.
# build.gradle.kts gives the task a formatter that lists only what is behind; versions-all names the plugin's own plain
# report, which also lists everything up to date.
versions: ## List the dependencies with newer versions, and any that could not be checked
	$(GRADLE) dependencyUpdates --no-configuration-cache --no-parallel

versions-all: ## The plugin's full dependency report, up-to-date dependencies included
	$(GRADLE) dependencyUpdates --no-configuration-cache --no-parallel --output-formatter=plain

depends: ## Print the dependency tree
	$(GRADLE) dependencies

# The build fails when the client's resolved npm packages differ from .kotlin-js-store/yarn.lock (build.gradle.kts), so
# after changing a Kotlin/JS or npm dependency, run this and commit the rewritten lock with the change.
yarn-unlock: ## Rewrite the committed Kotlin/JS yarn lock after npm dependency changes (commit the result)
	$(GRADLE) kotlinUpgradeYarnLock

# Also records the distribution's SHA-256 (distributionSha256Sum in gradle-wrapper.properties), from the .sha256 file
# Gradle publishes beside it, so the wrapper refuses a download that does not match.
upgrade-wrapper: ## Upgrade the Gradle wrapper (pick the version with GRADLE_VERSION=x.y.z), with its checksum
	@sum=$$(curl -fsSL https://services.gradle.org/distributions/gradle-$(GRADLE_VERSION)-bin.zip.sha256) \
		|| { echo "upgrade-wrapper: could not fetch the checksum of Gradle $(GRADLE_VERSION)" >&2; exit 1; }; \
	$(GRADLE) wrapper --gradle-version=$(GRADLE_VERSION) --distribution-type=bin --gradle-distribution-sha256-sum=$$sum

##@ Railway

# Restarts the latest deployment of each of RAILWAY_SERVICES, as it is: nothing is rebuilt or pulled, so a newly pushed
# image is not picked up (make railway-redeploy does that). Each walk is down for the few seconds it takes to start, so
# avoid class time. The CLI can return before the old process stops, so /ping alone could be answered by the process
# being replaced. /version also says when the server started, so this reads it from each of RAILWAY_URLS before
# restarting, then waits, up to two minutes each, until it changes, and shows it. Needs the Railway CLI, signed in
# (railway login); the project and services are named, so no railway link is needed.
railway-restart: ## Restart the Jermain and Moses services on Railway (no rebuild), then wait for each to restart
	@command -v railway >/dev/null || { echo "railway-restart: the Railway CLI is not installed" >&2; exit 1; }
	@befores=$$(for u in $(RAILWAY_URLS); do printf '%s\t%s\n' "$$u" "$$(curl -s -m 5 "$$u/version")"; done); \
	for s in $(RAILWAY_SERVICES); do \
		echo "Restarting $$s"; \
		railway restart --project $(RAILWAY_PROJECT) --environment $(RAILWAY_ENVIRONMENT) --service "$$s" --yes \
			|| { echo "railway-restart: could not restart $$s" >&2; exit 1; }; \
	done; \
	for u in $(RAILWAY_URLS); do \
		before=$$(printf '%s\n' "$$befores" | awk -F '\t' -v u="$$u" '$$1 == u { print $$2 }'); \
		printf '%s ' "$$u"; \
		for _ in $$(seq 1 60); do \
			v=$$(curl -s -m 5 "$$u/version"); \
			case "$$v" in "Version: "*) [ "$$v" != "$$before" ] && break;; esac; \
			printf .; sleep 2; \
		done; \
		case "$$v" in "Version: "*) [ "$$v" != "$$before" ];; *) false;; esac \
			|| { echo " did not restart within two minutes (it answers \"$$v\")" >&2; exit 1; }; \
		echo " $$v"; \
	done

# Deploys each of RAILWAY_SERVICES again from its source, pulling the newest pambrose/history-walk image from Docker Hub
# (railway redeploy --from-source; a plain redeploy, like a restart, reuses the image it has): what to run after make
# release. It first reads the version of the image it will pull, from the label the Dockerfile puts on it
# (org.opencontainers.image.version on DOCKER_REPO:latest), and says so if that is not this checkout's VERSION, which
# make release has yet to publish. The old deployment can answer until the new one is up, so /ping cannot tell which is
# running; instead it waits, up to five minutes each, until every RAILWAY_URLS reports that version at /version.
railway-redeploy: ## Redeploy Jermain and Moses on Railway from the newest Docker Hub image, then wait for its version
	@command -v railway >/dev/null || { echo "railway-redeploy: the Railway CLI is not installed" >&2; exit 1; }
	@expected=$$(docker buildx imagetools inspect $(DOCKER_REPO):latest \
		--format '{{ index (index .Image "linux/amd64").Config.Labels "org.opencontainers.image.version" }}' 2>/dev/null); \
	[ -n "$$expected" ] || { echo "railway-redeploy: could not read the version of $(DOCKER_REPO):latest on Docker" \
		"Hub; is Docker running?" >&2; exit 1; }; \
	echo "Docker Hub's $(DOCKER_REPO):latest is $$expected"; \
	[ "$$expected" = "$(VERSION)" ] || echo "  (this checkout is $(VERSION), which make release has not published yet)"; \
	for s in $(RAILWAY_SERVICES); do \
		echo "Redeploying $$s from the newest $(DOCKER_REPO) image"; \
		railway redeploy --from-source --project $(RAILWAY_PROJECT) --environment $(RAILWAY_ENVIRONMENT) \
			--service "$$s" --yes || { echo "railway-redeploy: could not redeploy $$s" >&2; exit 1; }; \
	done; \
	for u in $(RAILWAY_URLS); do \
		printf '%s ' "$$u"; \
		for i in $$(seq 1 100); do \
			curl -s -m 5 "$$u/version" | grep -q "^Version: $$expected " && break; printf .; sleep 3; \
		done; \
		v=$$(curl -s -m 5 "$$u/version"); \
		echo "$$v" | grep -q "^Version: $$expected " \
			|| { echo " still serves \"$$v\", not $$expected, after five minutes" >&2; exit 1; }; \
		echo " $$v"; \
	done

# Runs make served-version for each of RAILWAY_URLS, as PUBLIC_URL with no ORIGIN_URL (Railway serves the walks
# directly), so it shows the client bundle each walk serves next to this checkout's VERSION. Checks every walk, then
# fails if any one failed. Needs only curl.
railway-versions: ## Show the client bundle version each Railway walk serves (make served-version for each)
	@failed=; \
	for u in $(RAILWAY_URLS); do \
		echo "=== $$u"; \
		$(MAKE) --no-print-directory served-version PUBLIC_URL="$$u" ORIGIN_URL= || failed="$$failed $$u"; \
	done; \
	[ -z "$$failed" ] || { echo "railway-versions: served-version failed for$$failed" >&2; exit 1; }

# Fetches the most recent LOG_LINES lines of each walk's deployment log, the server's own output: its startup lines (the
# deck it serves, the database, the migrations), sign-ins, and errors. make log-jermain and make log-moses follow one.
log: ## Show the last LOG_LINES (100) lines of each walk's Railway log; SERVICE=Moses for one walk
	@command -v railway >/dev/null || { echo "log: the Railway CLI is not installed" >&2; exit 1; }
	@services="$(or $(SERVICE),$(RAILWAY_SERVICES))"; \
	for s in $$services; do \
		echo "=== $$s (last $(LOG_LINES) lines)"; \
		railway logs --project $(RAILWAY_PROJECT) --environment $(RAILWAY_ENVIRONMENT) --service "$$s" \
			--lines $(LOG_LINES) || { echo "log: could not read $$s's log" >&2; exit 1; }; \
	done

log-jermain: ## Follow the Jermain walk's Railway log like tail -f (its last TAIL_LINES lines, then each new one)
	@$(MAKE) --no-print-directory _follow-log FOLLOW_SERVICE=Jermain

log-moses: ## Follow the Moses walk's Railway log like tail -f (its last TAIL_LINES lines, then each new one)
	@$(MAKE) --no-print-directory _follow-log FOLLOW_SERVICE=Moses

# A walk's Railway log, followed like tail -f until Ctrl-C: its last TAIL_LINES lines, then, every FOLLOW_EVERY seconds,
# the lines written since. It polls rather than streams: railway logs follows a stream only when it writes straight to a
# terminal, and holds the new lines back when its output goes to a pipe, as it must here to be filtered. Each poll asks
# for the lines since the last one shown (--since, which works to the second, so it repeats a few), and drops those no
# newer than that line, comparing timestamps with their fractions padded to nanoseconds (they vary in length). The
# timestamps are Railway's, when it received each line, so they only move forward. Needs the Railway CLI and jq.
FOLLOW_EVERY ?= 3
FOLLOW_JQ_NORM = def norm: sub("Z$$"; "") | split(".") | .[0] + "." + (((.[1] // "") + "000000000")[0:9]);
_follow-log:
	@command -v railway >/dev/null || { echo "the Railway CLI is not installed" >&2; exit 1; }
	@command -v jq >/dev/null || { echo "jq is not installed (brew install jq)" >&2; exit 1; }
	@[ -n "$(FOLLOW_SERVICE)" ] || { echo "make log-jermain or make log-moses runs this" >&2; exit 1; }
	@args="--project $(RAILWAY_PROJECT) --environment $(RAILWAY_ENVIRONMENT) --service $(FOLLOW_SERVICE)"; \
	show() { jq -R -r --arg since "$$1" \
		'$(FOLLOW_JQ_NORM) fromjson? | select(((.timestamp // "") | norm) > ($$since | norm)) | .message // empty'; }; \
	latest() { jq -R -s -r --arg since "$$1" \
		'$(FOLLOW_JQ_NORM) [split("\n")[] | fromjson? | .timestamp // empty] | max_by(norm) // $$since'; }; \
	batch=$$(railway logs $$args --lines $(TAIL_LINES) --json) \
		|| { echo "could not read $(FOLLOW_SERVICE)'s log" >&2; exit 1; }; \
	echo "=== $(FOLLOW_SERVICE) (following every $(FOLLOW_EVERY)s; Ctrl-C stops)"; \
	printf '%s\n' "$$batch" | show ""; \
	since=$$(printf '%s\n' "$$batch" | latest "$$(date -u +%Y-%m-%dT%H:%M:%SZ)"); \
	while sleep $(FOLLOW_EVERY); do \
		batch=$$(railway logs $$args --since "$$since" --json 2>/dev/null) || continue; \
		printf '%s\n' "$$batch" | show "$$since"; \
		since=$$(printf '%s\n' "$$batch" | latest "$$since"); \
	done

##@ Cloudflare

# Shows the client bundle's version as PUBLIC_URL serves it, beside this checkout's VERSION, and fails when it finds
# none. The version is the line App.start() logs, "History Walk version VERSION - release date". It fetches with GET,
# not HEAD. With ORIGIN_URL, PUBLIC_URL is the address Cloudflare serves and ORIGIN_URL the deployment behind it: it
# shows both bundles and Cloudflare's cf-cache-status, and also fails when Cloudflare serves another bundle than the
# origin, or caches the bundle, which a deploy then does not replace: purge PUBLIC_URL/main.bundle.js in Cloudflare.
# Without ORIGIN_URL it says nothing of Cloudflare, and shows a cf-cache-status only when the response carries one; a
# caching one (Cloudflare in front after all) still fails.
BUNDLE_VERSION_RE = History Walk version [0-9]+\.[0-9]+\.[0-9]+ - [^"]+
served-version: ## Show the bundle version PUBLIC_URL serves; with ORIGIN_URL, check Cloudflare serves the origin's
	@[ -n "$(PUBLIC_URL)" ] || { echo "Set PUBLIC_URL (and ORIGIN_URL), e.g. make served-version PUBLIC_URL=https://walk.example.org ORIGIN_URL=https://<service>.up.railway.app" >&2; exit 1; }
	@problems=0; problem() { echo "  - $$1" >&2; problems=$$((problems + 1)); }; \
	served() { curl -sS --compressed -m 30 -D - "$$1/main.bundle.js" \
		| LC_ALL=C grep -aoiE '^cf-cache-status: [a-z]+|$(BUNDLE_VERSION_RE)'; }; \
	version() { grep -iv '^cf-cache-status' | head -1; }; \
	echo "This checkout:       $(VERSION)"; \
	origin=; \
	if [ -n "$(ORIGIN_URL)" ]; then \
		origin=$$(served "$(ORIGIN_URL)" | version); \
		echo "The origin's bundle: $${origin:-not found}"; \
	fi; \
	fetched=$$(served "$(PUBLIC_URL)"); \
	public=$$(echo "$$fetched" | version); \
	cached=$$(echo "$$fetched" | sed -n 's/^[Cc][Ff]-[Cc][Aa][Cc][Hh][Ee]-[Ss][Tt][Aa][Tt][Uu][Ss]: //p'); \
	if [ -n "$(ORIGIN_URL)" ]; then \
		echo "Cloudflare's bundle: $${public:-not found} (cf-cache-status: $${cached:-none})"; \
	else \
		echo "The served bundle:   $${public:-not found}$${cached:+ (cf-cache-status: $$cached)}"; \
	fi; \
	[ -n "$$public" ] || problem "found no version in $(PUBLIC_URL)/main.bundle.js"; \
	[ -z "$$origin" ] || [ -z "$$public" ] || [ "$$origin" = "$$public" ] \
		|| problem "Cloudflare serves another bundle than the origin: purge $(PUBLIC_URL)/main.bundle.js in Cloudflare"; \
	case "$$cached" in ""|BYPASS|DYNAMIC) ;; \
		*) problem "Cloudflare caches the bundle ($$cached), so a deploy keeps its copy: see Caching in the docs' Releases";; \
	esac; \
	[ $$problems -eq 0 ] || exit 1

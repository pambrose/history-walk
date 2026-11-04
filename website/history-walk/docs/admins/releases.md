# Releases and updates

## Versions

Two numbers in `gradle.properties` describe a release:

`version`
:   The release, such as `1.1.2`. `/version` reports it, the page logs it in the browser's console, and it tags the
    Docker image. Bump it for every release.

`apiVersion`
:   The version of the interface between the page and the server. Increment it **only** when that interface
    (`IContentService` and the data it carries) changes, or when a release must replace pages already open. A
    change of `apiVersion` makes every open page reload (below); a release that leaves it alone reaches each page at
    its next load.

## Shipping a release

1. Bump `version` in `gradle.properties` (and `apiVersion`, if the interface changed), and add the version's sections
   to `CHANGELOG.md` (`## [1.2.0] - <date>`) and `RELEASE_NOTES.md` (`## History Walk 1.2.0 — <date>`, ending with a
   `**Full Changelog**:` link). Merge it all to `master`, with CI green.
2. On an up-to-date `master`, run `make release-check`. It publishes nothing; it lists every reason the checkout
   can't be released yet: a dirty tree, another branch, a commit not on GitHub, a missing changelog or release-notes
   section, a tag or GitHub release that already exists, CI that hasn't passed for the commit, Docker not running, or
   the version already on Docker Hub.
3. Run `make release`. It runs `release-check` again, a clean build and every CI gate (`make ci`), then asks once.
   Then it pushes the image to Docker Hub (`pambrose/history-walk:<version>` and `:latest`, amd64 and arm64) and
   creates the GitHub release: tag `1.2.0`, title `v1.2.0`, and the version's section of `RELEASE_NOTES.md` as its
   notes. If the checkout changes while `make ci` runs, it publishes nothing.
4. Deploy each walk: on Railway, `make railway-redeploy` pulls the new image and waits until each walk reports the new
   version. Each deployment migrates its database as it starts.
5. Behind Cloudflare, check that Cloudflare serves the new page bundle ([below](#after-a-deploy)).

## What open pages do

An open page asks the server every minute (`CLIENT_VERSION_CHECK_INTERVAL_SECS`) whether it is current. The server
compares only the page's `apiVersion` with its own.

- **Same `apiVersion`**: nothing happens. The page keeps working, and runs the new release's page the next time it is
  loaded.
- **Different `apiVersion`**: the page shows an **Application Update** dialog, *A new version of History Walk is
  available. Please click here to reload this page and continue.* A minute later
  (`CLIENT_RELOAD_WARNING_INTERVAL_SECS`), it shows a countdown, *Reloading this page in N seconds…*, and reloads
  itself.

The dialog and the reload wait while another dialog is open or a student is writing a reason, so nothing typed is
lost; every move before that is already saved. If a reload brings back the old page (a cache still serving the old
bundle), the page doesn't loop: it shows the dialog once and stops reloading itself in that tab.

## Caching, and Cloudflare

The page's script, `main.bundle.js`, has the same name in every release, so caches must check it every time. The
server makes sure they do:

| What | `Cache-Control` | Effect |
|---|---|---|
| The page (`/`), `main.bundle.js`, and images under `/static` | `no-cache, private` with an `ETag` | Browsers ask each time and get a short *304 Not Modified* when nothing changed; a new release is picked up at the next load. Cloudflare keeps no copy. |
| Sign-in, sign-out, the reset, the class pages, the admin page | `no-store, private` | Never stored anywhere, so Back after a sign-out on a shared computer shows nothing. |

Don't add a Cloudflare cache rule that ignores the origin's headers (such as *Cache Everything* with an edge TTL): it
would serve old bundles after a release, and could cache per-user pages.

### After a deploy

A change to these headers doesn't clear a copy Cloudflare already holds. After a deploy, compare the bundle the deployment
serves (the origin) with the one Cloudflare serves:

```bash
make served-version PUBLIC_URL=https://<public address> ORIGIN_URL=https://<origin address>
```

It prints the version in this checkout, in the origin's bundle, and in Cloudflare's, with Cloudflare's `cf-cache-status`,
and fails if they differ or Cloudflare is caching the bundle. If it fails, purge `main.bundle.js` in Cloudflare's
dashboard (Caching → Configuration → Purge Cache → Custom Purge) and run it again.

## Dependencies

| Command | Does |
|---|---|
| `make versions` | Lists the dependencies that have newer versions. |
| `make versions-all` | The full dependency report. |
| `make upgrade-wrapper GRADLE_VERSION=x.y.z` | Upgrades the Gradle wrapper. |

Dependabot proposes updates to GitHub Actions and to the Docker base image, and security fixes for the Java
libraries. Other library updates are made by hand from `make versions`.

!!! warning "KSP is held at 2.3.12"

    KSP 2.3.13 sometimes drops library symbols when two KSP tasks run at once in one Gradle process
    ([google/ksp#3286](https://github.com/google/ksp/issues/3286)). The build then fails with *The 'expect'
    declaration 'getService' has no 'actual' declaration*. `gradle/libs.versions.toml` holds KSP at 2.3.12 until a
    release fixes it.

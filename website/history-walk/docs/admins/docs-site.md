# These docs

This site is built with [Zensical](https://zensical.org) from Markdown files in the repository:

| Path | Holds |
|---|---|
| `website/pyproject.toml` | The Python project that installs Zensical (`uv sync` installs it into `website/.venv`). |
| `website/history-walk/zensical.toml` | The site's settings: its name, navigation, theme, and Markdown extensions. |
| `website/history-walk/docs/` | The pages: `teachers/` and `admins/`. |

## Previewing and building

From the top of the repository:

| Command | Does |
|---|---|
| `make website-serve` | Serves the site at `http://localhost:8000`, rebuilding it on every save. `WEBSITE_ADDR=0.0.0.0:8000` serves it to the network. |
| `make website-build` | Builds the static site into `website/history-walk/site`. It fails on any warning, such as a link to a page that doesn't exist. |
| `make website-clean` | Removes the built site and Zensical's cache. |
| `make upgrade-site` | Upgrades Zensical and its dependencies in `website/uv.lock` to their latest releases and installs them, printing the old and new Zensical versions. Run `make website-build` afterwards to check that the site still builds cleanly. |

Each target first installs Zensical, at the version `website/uv.lock` pins, into `website/.venv` with
[uv](https://docs.astral.sh/uv/); uv is the only thing to install by hand.

`site/` is plain HTML, CSS, and JavaScript: any static host can serve it.

## Publishing

The site is published to GitHub Pages, at <https://pambrose.github.io/history-walk/>, by the **Website** workflow
(`.github/workflows/website.yml`):

- A push to `master` that changes anything under `website/` (or the workflow) builds the site with
  `make website-build` and deploys it. **Run workflow** on the workflow's Actions page publishes it by hand.
- A pull request that changes `website/` gets the same build as a check, so a broken link fails the pull request; it
  publishes nothing.
- Deploys run one at a time and are never cancelled halfway.

GitHub Pages must be set up once to take its content from the workflow: in the repository's **Settings → Pages →
Build and deployment**, set **Source** to **GitHub Actions**. Until then the deploy job fails, saying Pages is not
enabled.

The address is set as `site_url` in `zensical.toml`. To publish under a custom domain, set it in **Settings → Pages →
Custom domain**, and change `site_url` to match.

## Editing

- A new page needs an entry in `nav` in `zensical.toml`, or it won't appear in the navigation.
- `docs/llms.txt` is a symlink to the repository's `llms.txt`, so the site serves the same file at
  <https://pambrose.github.io/history-walk/llms.txt>. Edit the one at the top of the repository.
- `docs/release-notes.md` is a symlink to the repository's `RELEASE_NOTES.md`, so the site's **Release notes** page is
  the same text `make release` uses for each GitHub release. Edit the one at the top of the repository.
- Dark mode is the default: the first `[[project.theme.palette]]` entry (`slate`) is what a first visit gets, and the
  toggle in the header switches to light. The browser remembers the choice.
- The pages use admonitions (`!!! note`), collapsible questions (`??? question`), tables, and Mermaid diagrams; see
  Zensical's authoring docs for the syntax.

When the app changes in a way a teacher or an administrator would notice, update the page that describes it in the
same pull request.

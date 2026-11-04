# Google sign-in

Everyone signs in with Google. Each deployment needs an OAuth client in Google Cloud, and three environment variables
that point at it: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `OAUTH_CALLBACK_URL`.

## How sign-in works

1. The page sends the browser to `/auth/login`, which redirects to Google with the client ID, the callback URL, the
   school's domain as a hint (`hd=athenian.org`, so the account chooser offers only school accounts), and
   `prompt=select_account` (so the chooser always appears).
2. The user picks an account. Google sends the browser to the callback URL with a one-time code.
3. The server checks that the callback belongs to a sign-in this browser started (a signed state, bound to a short-lived
   cookie, with PKCE), exchanges the code for the user's profile, and decides:
    - an address in the school's domain that Google has verified, and
    - either on `TEACHER_EMAILS` (a teacher), or starting with two digits (a student).
4. An admitted user gets an encrypted session cookie for 12 hours, and the server records their name, address, and
   role. Anyone else goes back to the sign-in dialog with a message saying why.

The server keeps nothing between steps 1 and 3: the state travels in the URL and a cookie, so a restart during
someone's sign-in costs them only a retry.

## Setting up a client

Do this once per deployment, or once for several, listing each deployment's callback.

1. In the [Google Cloud console](https://console.cloud.google.com/), create or choose a project, ideally in the
   **athenian.org organization**.
2. Configure the **OAuth consent screen** (Google Auth Platform → Branding and Audience):
    - **User type: Internal**, so only athenian.org Workspace accounts can sign in. This needs a project in the
      athenian.org organization. An *External* app in *Testing* status admits only its listed test users, which
      would turn students away.
    - **App name**: *History Walk*. Google shows it on the consent screen, so it should be the name students expect.
3. Create an **OAuth client ID** of type **Web application** (Google Auth Platform → Clients). Copy its **client ID**
   and **client secret** into `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.
4. Under **Authorized redirect URIs**, add every callback URL that will use this client, exactly:

    | Where | Redirect URI |
    |---|---|
    | A Railway deployment | `https://<service>.up.railway.app/auth/callback` |
    | A custom domain | `https://<domain>/auth/callback` |
    | Running the jar, `make server`, or `make docker-run` locally | `http://localhost:8080/auth/callback` |
    | `make client` (the dev server on port 3000) | `http://localhost:3000/auth/callback` |

5. Set `OAUTH_CALLBACK_URL` on each deployment to its own redirect URI from that list, and restart it.

Changes to redirect URIs can take a few minutes to reach Google's sign-in servers.

!!! warning "Use `https` for every deployed callback"

    Google accepts plain `http` redirect URIs only for `localhost`. A deployed `OAUTH_CALLBACK_URL` that starts with
    `http://` can never match, so every sign-in fails with `redirect_uri_mismatch`, even though the site itself is
    served over `https`.

## Error 400: redirect_uri_mismatch { #redirect_uri_mismatch }

Google shows *Access blocked: This app's request is invalid* with *Error 400: redirect_uri_mismatch* when the
`redirect_uri` the app sent is not one of the client's authorized redirect URIs. It happens before the user picks an
account, so the page shows whatever account the browser has signed in to Google, often a personal one; the account is
not the cause.

To find the mismatch:

1. See what the server sends. The `Location` header of `/auth/login` is the URL it sends browsers to:

    ```bash
    curl -s -o /dev/null -D - https://<deployment>/auth/login | grep -i '^location'
    ```

    Its `redirect_uri=` parameter (URL-encoded) is the deployment's `OAUTH_CALLBACK_URL`, and `client_id=` the client.

2. Compare it, character for character, with the client's **Authorized redirect URIs**: scheme (`https`), host,
   port, path (`/auth/callback`), and no trailing slash.

3. Fix whichever is wrong: change `OAUTH_CALLBACK_URL` (the deployment restarts), or add the URI to the client.

You can also ask Google directly whether it accepts a redirect URI for a client, without signing in. An accepted one
redirects to Google's sign-in page; a refused one redirects to an error page:

```bash
curl -s -o /dev/null -w '%{redirect_url}\n' \
  'https://accounts.google.com/o/oauth2/v2/auth?client_id=<client id>&response_type=code&scope=openid%20email&redirect_uri=https%3A%2F%2F<host>%2Fauth%2Fcallback'
```

A `redirect_url` containing `/signin/oauth/error` means Google refuses that redirect URI for that client.

## Other sign-in problems

| Symptom | Cause | Fix |
|---|---|---|
| *… isn't an athenian.org account* | The user chose another account. | Sign in with the school account. |
| *… isn't set up to use History Walk* | A school address that is neither on `TEACHER_EMAILS` nor starts with two digits. | Add it to `TEACHER_EMAILS` if it's a teacher's. See [Teachers and students](access.md). |
| *Sign-in didn't complete* | The state cookie was missing or stale (a sign-in left open over 10 minutes, Back during sign-in, cookies blocked), or Google refused the code. | Try again; check that the browser accepts cookies for the site. |
| The server won't start: *SESSION_SECRET must be set for a server reached at …* | `OAUTH_CALLBACK_URL` is not local, and `SESSION_SECRET` is empty. | Set it (`openssl rand -hex 32`). |
| *SESSION_SECRET is too weak* | Fewer than 16 characters. | Generate a new one. |
| *Missing GOOGLE_CLIENT_ID value* (or the secret, or the callback) | The variable isn't set. | Set it. |
| Everyone is signed out | `SESSION_SECRET` changed. | Expected; they sign in again. |

## Secrets

The client secret and `SESSION_SECRET` belong in the host's settings, never in the repository. To replace a client
secret, add a new one to the client in Google Cloud, update `GOOGLE_CLIENT_SECRET`, and then delete the old one.

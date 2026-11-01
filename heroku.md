## Heroku Configuration Values

### Jermain

DATABASE_URL (assigned)
PAPERTRAIL_API_TOKEN (assigned)
ALLOW_SLIDE_ACCESS = true
GRADLE_TASK = -Pprod=true jar
SLIDES_REPO_FILENAME = JermainLonguen.kt
SLIDES_VARIABLE_NAME = jermain
GOOGLE_CLIENT_ID = (from Google Cloud)
GOOGLE_CLIENT_SECRET = (from Google Cloud)
OAUTH_CALLBACK_URL = https://<jermain-app>.herokuapp.com/auth/callback
SESSION_SECRET = (openssl rand -hex 32)
TEACHER_EMAILS = (comma-separated teacher addresses)
LOGIN_GREETING = (optional; sign-in dialog title, default "History Walk")

### Moses

DATABASE_URL (assigned)
PAPERTRAIL_API_TOKEN (assigned)
ALLOW_SLIDE_ACCESS = true
GRADLE_TASK = -Pprod=true jar
SLIDES_REPO_FILENAME = Moses.kt
SLIDES_VARIABLE_NAME = moses
GOOGLE_CLIENT_ID = (from Google Cloud)
GOOGLE_CLIENT_SECRET = (from Google Cloud)
OAUTH_CALLBACK_URL = https://<moses-app>.herokuapp.com/auth/callback
SESSION_SECRET = (openssl rand -hex 32)
TEACHER_EMAILS = (comma-separated teacher addresses)
LOGIN_GREETING = (optional; sign-in dialog title, default "History Walk")


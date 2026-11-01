-- A session cookie carries the user's session_version from sign-in, and SessionValidation signs out a cookie whose
-- version is no longer the stored one. Signing out increments it, which ends the user's sessions in every browser,
-- copied cookies included. Existing users start at 0, the version a cookie written before this column reads as, so
-- their sessions stay valid until they next sign out.
ALTER TABLE history.users
    ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0;

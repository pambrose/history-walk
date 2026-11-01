drop schema if exists history cascade;

create schema history;

-- Users sign in with Google; emails are stored lowercased.
CREATE TABLE history.users
(
    id             UUID UNIQUE PRIMARY KEY,
    created        TIMESTAMPTZ DEFAULT NOW(),
    updated        TIMESTAMPTZ DEFAULT NOW(),
    email          TEXT NOT NULL UNIQUE
        CONSTRAINT users_email_lowercase CHECK (email = lower(email)),
    full_name      TEXT NOT NULL,
    picture_url    TEXT,
    role           TEXT NOT NULL CHECK (role IN ('student', 'teacher')),
    last_sign_in   TIMESTAMPTZ,
    last_path_name TEXT NOT NULL
);

CREATE TABLE history.user_choices
(
    id             SERIAL UNIQUE PRIMARY KEY,
    created        TIMESTAMPTZ DEFAULT NOW(),
    updated        TIMESTAMPTZ DEFAULT NOW(),
    user_uuid_ref  UUID REFERENCES history.users ON DELETE CASCADE,
    from_path_name TEXT    NOT NULL,
    from_title     TEXT    NOT NULL,
    to_path_name   TEXT    NOT NULL,
    to_title       TEXT    NOT NULL,
    dead_end       BOOLEAN NOT NULL,
    choice_text    TEXT    NOT NULL,
    reason         TEXT    NOT NULL,

    CONSTRAINT user_choices_unique unique (user_uuid_ref, from_path_name, to_path_name)
);

-- Progress views report students only, so teachers' test walks never appear.
CREATE VIEW history.user_visits AS
SELECT users.id, to_title
FROM history.users,
     history.user_choices
WHERE history.user_choices.user_uuid_ref = users.id
  AND users.role = 'student';


CREATE VIEW history.user_decision_counts AS
SELECT users.id, full_name, email, last_path_name, count(user_uuid_ref) as decision_count
FROM history.users
         LEFT OUTER JOIN history.user_choices
                         ON history.user_choices.user_uuid_ref = users.id
WHERE users.role = 'student'
GROUP BY users.id, users.full_name
ORDER BY users.id, users.full_name;

CREATE VIEW history.user_reasons AS
SELECT users.id,
       full_name,
       email,
       from_path_name,
       to_path_name,
       from_title,
       to_title,
       reason
FROM history.users
         JOIN history.user_choices
              ON history.user_choices.user_uuid_ref = users.id
WHERE users.role = 'student'
ORDER BY users.email, user_choices.created;

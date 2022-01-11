drop schema if exists history cascade;

create schema history;

CREATE TABLE history.users
(
    id             UUID UNIQUE PRIMARY KEY,
    created        TIMESTAMPTZ DEFAULT NOW(),
    updated        TIMESTAMPTZ DEFAULT NOW(),
    email          TEXT NOT NULL UNIQUE,
    full_name      TEXT NOT NULL,
    salt           TEXT NOT NULL,
    digest         TEXT NOT NULL,
    last_path_name TEXT NOT NULL
);

CREATE TABLE history.userchoices
(
    id             SERIAL UNIQUE PRIMARY KEY,
    created        TIMESTAMPTZ DEFAULT NOW(),
    updated        TIMESTAMPTZ DEFAULT NOW(),
    user_uuid_ref  UUID REFERENCES history.users ON DELETE CASCADE,
    from_path_name TEXT NOT NULL,
    from_title     TEXT NOT NULL,
    to_path_name   TEXT NOT NULL,
    to_title       TEXT NOT NULL,
    choice_text    TEXT NOT NULL,
    reason         TEXT NOT NULL,

    CONSTRAINT user_choices_unique unique (user_uuid_ref, from_path_name, to_path_name)
);

CREATE VIEW history.user_visits AS
SELECT users.id, to_title
FROM history.users,
     history.userchoices
WHERE history.userchoices.user_uuid_ref = users.id;


CREATE VIEW history.user_decision_counts AS
SELECT users.id, full_name, email, last_path_name, count(user_uuid_ref) as decision_count
FROM history.users
         LEFT OUTER JOIN history.userchoices
                         ON history.userchoices.user_uuid_ref = users.id
GROUP BY users.id, users.full_name
ORDER BY users.id, users.full_name;

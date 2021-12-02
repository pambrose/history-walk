drop schema if exists history cascade;

create schema history;

CREATE TABLE history.users
(
    id             BIGSERIAL UNIQUE PRIMARY KEY,
    created        TIMESTAMPTZ DEFAULT NOW(),
    updated        TIMESTAMPTZ DEFAULT NOW(),
    uuid           UUID NOT NULL UNIQUE,
    email          TEXT NOT NULL UNIQUE,
    full_name      TEXT NOT NULL,
    salt           TEXT NOT NULL,
    digest         TEXT NOT NULL,
    last_path_name TEXT NOT NULL
);

CREATE TABLE history.userchoices
(
    id             BIGSERIAL UNIQUE PRIMARY KEY,
    created        TIMESTAMPTZ DEFAULT NOW(),
    updated        TIMESTAMPTZ DEFAULT NOW(),
    uuid           UUID NOT NULL UNIQUE,
    user_uuid      UUID NOT NULL,
    from_path_name TEXT NOT NULL,
    from_title     TEXT NOT NULL,
    to_path_name   TEXT NOT NULL,
    to_title       TEXT NOT NULL,
    choice_text    TEXT NOT NULL,
    reason         TEXT NOT NULL,

    CONSTRAINT user_choices_unique unique (user_uuid, from_path_name, to_path_name)
);
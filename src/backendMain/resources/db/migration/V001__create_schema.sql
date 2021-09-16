drop schema if exists history cascade;

create schema history;

CREATE TABLE history.users
(
    id         BIGSERIAL UNIQUE PRIMARY KEY,
    created    TIMESTAMPTZ DEFAULT NOW(),
    updated    TIMESTAMPTZ DEFAULT NOW(),
    uuid       UUID NOT NULL UNIQUE,
    email      TEXT NOT NULL UNIQUE,
    full_name  TEXT NOT NULL,
    salt       TEXT NOT NULL,
    digest     TEXT NOT NULL,
    last_title TEXT NOT NULL
);

CREATE TABLE history.userchoices
(
    id         BIGSERIAL UNIQUE PRIMARY KEY,
    created    TIMESTAMPTZ DEFAULT NOW(),
    updated    TIMESTAMPTZ DEFAULT NOW(),
    uuid       UUID NOT NULL UNIQUE,
    user_uuid  UUID NOT NULL,
    from_title TEXT NOT NULL,
    abbrev     TEXT NOT NULL,
    title      TEXT NOT NULL,
    reason     TEXT NOT NULL,

    CONSTRAINT user_choices_unique unique (user_uuid, from_title, title)
);
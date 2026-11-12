-- Moves the stored places (users.last_path_name) and moves (user_choices) of the slides that release renamed or
-- rerouted, so a student on one of them keeps their place and their reasons (docs/code-review-2026-11-11.md, Wave 3).
-- A slide's path name is the chain of titles from the root, so each change below moves every path that runs through
-- it. Each step is a text replacement of whole titles or chains of titles, which occur nowhere else in either deck,
-- so on the Moses database the Jermain steps change nothing, and the other way round. DbmsMigrateTest checks that every
-- place either deck had before this migration still finds its slide after it.

-- 1. Title typos (issue 11): Moses's "Decison" in 26 titles, and four Jermain titles with a lowercase verb.
CREATE FUNCTION pg_temp.renamed(t TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE AS
$$
SELECT replace(replace(replace(replace(replace(t,
    'Decison', 'Decision'),
    'Major Encounter: bluff Patrol', 'Major Encounter: Bluff Patrol'),
    'Major Encounter: fight 2nd Patrol', 'Major Encounter: Fight 2nd Patrol'),
    'Major Encounter (via Ross): fight 1st Patrol', 'Major Encounter (via Ross): Fight 1st Patrol'),
    'Major Encounter (via Ross): bluff 1st Patrol', 'Major Encounter (via Ross): Bluff 1st Patrol')
$$;

UPDATE history.users
SET last_path_name = pg_temp.renamed(last_path_name)
WHERE last_path_name <> pg_temp.renamed(last_path_name);

UPDATE history.user_choices
SET from_path_name = pg_temp.renamed(from_path_name),
    from_title     = pg_temp.renamed(from_title),
    to_path_name   = pg_temp.renamed(to_path_name),
    to_title       = pg_temp.renamed(to_title)
WHERE from_path_name <> pg_temp.renamed(from_path_name)
   OR to_path_name <> pg_temp.renamed(to_path_name);

DROP FUNCTION pg_temp.renamed(TEXT);

-- 2. Seasons (issue 13). Leaving in the summer on Old Ben's advice via Ross now goes through Summer Departure, and
-- leaving in the spring on Old Ben's own advice through Spring Departure, as on every other path. The decision itself
-- now leads to the departure slide; every place and move beyond it gains that slide in its path.
UPDATE history.user_choices
SET to_path_name = from_path_name || '/Summer Departure',
    to_title     = 'Summer Departure'
WHERE right(from_path_name, length('/Old Ben (via Ross): When to Go')) = '/Old Ben (via Ross): When to Go'
  AND to_path_name = from_path_name || '/Summer Cross-Country';

UPDATE history.user_choices
SET to_path_name = from_path_name || '/Spring Departure',
    to_title     = 'Spring Departure'
WHERE right(from_path_name, length('/Go To Old Ben')) = '/Go To Old Ben'
  AND to_path_name = from_path_name || '/Paddy Rollers';

UPDATE history.users
SET last_path_name = replace(last_path_name, '/Old Ben (via Ross): When to Go/Summer Cross-Country',
                             '/Old Ben (via Ross): When to Go/Summer Departure/Summer Cross-Country')
WHERE strpos(last_path_name, '/Old Ben (via Ross): When to Go/Summer Cross-Country') > 0;

UPDATE history.user_choices
SET from_path_name = replace(from_path_name, '/Old Ben (via Ross): When to Go/Summer Cross-Country',
                             '/Old Ben (via Ross): When to Go/Summer Departure/Summer Cross-Country'),
    to_path_name   = replace(to_path_name, '/Old Ben (via Ross): When to Go/Summer Cross-Country',
                             '/Old Ben (via Ross): When to Go/Summer Departure/Summer Cross-Country')
WHERE strpos(from_path_name, '/Old Ben (via Ross): When to Go/Summer Cross-Country') > 0
   OR strpos(to_path_name, '/Old Ben (via Ross): When to Go/Summer Cross-Country') > 0;

UPDATE history.users
SET last_path_name = replace(last_path_name, '/Go To Old Ben/Paddy Rollers', '/Go To Old Ben/Spring Departure/Paddy Rollers')
WHERE strpos(last_path_name, '/Go To Old Ben/Paddy Rollers') > 0;

UPDATE history.user_choices
SET from_path_name = replace(from_path_name, '/Go To Old Ben/Paddy Rollers',
                             '/Go To Old Ben/Spring Departure/Paddy Rollers'),
    to_path_name   = replace(to_path_name, '/Go To Old Ben/Paddy Rollers', '/Go To Old Ben/Spring Departure/Paddy Rollers')
WHERE strpos(from_path_name, '/Go To Old Ben/Paddy Rollers') > 0
   OR strpos(to_path_name, '/Go To Old Ben/Paddy Rollers') > 0;

-- 3. Refusing Ross's deal (issue 6). Its "No" led to the via-Ross slides, which assume Ross's papers and pistols; it now
-- leads to Old Ben's own advice (Go To Old Ben). Summer, autumn, and spring below it have the same slides as before.
-- Winter does not: the via-Ross winter (Steal an Overcoat (via Ross) and everything after it) is gone from this branch.
-- A student there goes back to the branch's first slide and chooses when to go again. Their moves there are left as
-- they are: they happened, and the class pages still show them, but they no longer lead anywhere.
UPDATE history.users
SET last_path_name =
        left(last_path_name, strpos(last_path_name,
                                    '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go/Steal an Overcoat (via Ross)') -
                             1) || '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go'
WHERE strpos(last_path_name, '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go/Steal an Overcoat (via Ross)') > 0;

UPDATE history.users
SET last_path_name = replace(last_path_name, '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go',
                             '/Ross Encounter, then Ben/Go To Old Ben')
WHERE strpos(last_path_name, '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go') > 0;

UPDATE history.user_choices
SET from_path_name = replace(from_path_name, '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go',
                             '/Ross Encounter, then Ben/Go To Old Ben'),
    to_path_name   = replace(to_path_name, '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go',
                             '/Ross Encounter, then Ben/Go To Old Ben'),
    from_title     = CASE
                         WHEN right(from_path_name, length('/Ross Encounter, then Ben/Old Ben (via Ross): When to Go')) =
                              '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go' THEN 'Go To Old Ben'
                         ELSE from_title END,
    to_title       = CASE
                         WHEN right(to_path_name, length('/Ross Encounter, then Ben/Old Ben (via Ross): When to Go')) =
                              '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go' THEN 'Go To Old Ben'
                         ELSE to_title END
WHERE (strpos(from_path_name, '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go') > 0
    OR strpos(to_path_name, '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go') > 0)
  AND strpos(from_path_name, '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go/Steal an Overcoat (via Ross)') = 0
  AND strpos(to_path_name, '/Ross Encounter, then Ben/Old Ben (via Ross): When to Go/Steal an Overcoat (via Ross)') = 0;

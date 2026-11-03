-- The admin page's switch: while teachers_see_student_view is on, every teacher walks as a student does, so teachers
-- can try the app as their class will before giving it to them. One row, the app's settings, which starts with
-- teachers seeing the teacher view, as they did before the switch.
CREATE TABLE history.settings
(
    id                        INTEGER PRIMARY KEY CHECK (id = 1),
    teachers_see_student_view BOOLEAN NOT NULL DEFAULT FALSE,
    updated                   TIMESTAMPTZ DEFAULT NOW()
);

INSERT INTO history.settings (id)
VALUES (1);

-- A move a teacher made while teachers saw the student view. The class pages count it as a student's, and go on
-- counting it after the switch goes back, until the admin page's clear deletes every teacher's walk. A student's
-- moves are counted by the student's role and never set it.
ALTER TABLE history.user_choices
    ADD COLUMN counted_as_student BOOLEAN NOT NULL DEFAULT FALSE;

-- The progress views count students' moves and teachers' counted ones. A teacher appears in the summary once they
-- have a counted move, with only their counted moves in the count.
CREATE OR REPLACE VIEW history.user_visits AS
SELECT users.id, to_title
FROM history.users,
     history.user_choices
WHERE history.user_choices.user_uuid_ref = users.id
  AND (users.role = 'student' OR user_choices.counted_as_student);

CREATE OR REPLACE VIEW history.user_decision_counts AS
SELECT users.id, full_name, email, last_path_name, count(user_uuid_ref) as decision_count
FROM history.users
         LEFT OUTER JOIN history.user_choices
                         ON history.user_choices.user_uuid_ref = users.id
                             AND (users.role = 'student' OR user_choices.counted_as_student)
WHERE users.role = 'student'
   OR EXISTS (SELECT 1
              FROM history.user_choices counted
              WHERE counted.user_uuid_ref = users.id
                AND counted.counted_as_student)
GROUP BY users.id, users.full_name
ORDER BY users.id, users.full_name;

CREATE OR REPLACE VIEW history.user_reasons AS
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
   OR user_choices.counted_as_student
ORDER BY users.email, user_choices.created;

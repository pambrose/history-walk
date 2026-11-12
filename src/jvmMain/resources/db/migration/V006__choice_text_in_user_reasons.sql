-- The reasons page names the choice each reason explains. The slides a choice leads to do not always say: every wrong
-- Moses answer leads to a slide titled Incorrect1, Incorrect2, or Incorrect3, so the titles alone could not tell a
-- teacher which answer a student chose. CREATE OR REPLACE VIEW keeps the view's columns as they were and adds the new
-- one at the end, which is the only place Postgres lets it go.
CREATE OR REPLACE VIEW history.user_reasons AS
SELECT users.id,
       full_name,
       email,
       from_path_name,
       to_path_name,
       from_title,
       to_title,
       reason,
       choice_text
FROM history.users
         JOIN history.user_choices
              ON history.user_choices.user_uuid_ref = users.id
WHERE users.role = 'student'
   OR user_choices.counted_as_student
ORDER BY users.email, user_choices.created;

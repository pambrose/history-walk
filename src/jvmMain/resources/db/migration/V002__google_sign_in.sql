-- Emails are stored lowercased from here on; normalise rows created before.
UPDATE history.users SET email = lower(email);
ALTER TABLE history.users ADD CONSTRAINT users_email_lowercase CHECK (email = lower(email));

-- Google sign-in replaces passwords: drop the credential columns, record the profile picture, role and last sign-in.
ALTER TABLE history.users
  DROP COLUMN salt,
  DROP COLUMN digest,
  ADD COLUMN picture_url TEXT,
  ADD COLUMN role TEXT NOT NULL DEFAULT 'student' CHECK (role IN ('student', 'teacher')),
  ADD COLUMN last_sign_in TIMESTAMPTZ;

-- Progress views report students only.
CREATE OR REPLACE VIEW history.user_visits AS
SELECT users.id, to_title
FROM history.users,
     history.user_choices
WHERE history.user_choices.user_uuid_ref = users.id
  AND users.role = 'student';

CREATE OR REPLACE VIEW history.user_decision_counts AS
SELECT users.id, full_name, email, last_path_name, count(user_uuid_ref) as decision_count
FROM history.users
         LEFT OUTER JOIN history.user_choices
                         ON history.user_choices.user_uuid_ref = users.id
WHERE users.role = 'student'
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
ORDER BY users.email, user_choices.created;

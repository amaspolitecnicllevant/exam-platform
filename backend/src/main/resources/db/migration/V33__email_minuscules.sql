-- Els correus es desen en minúscules (User.normalitzaEmail) i es cerquen sense distingir-les.
UPDATE users SET email = lower(trim(email)) WHERE email <> lower(trim(email));
CREATE UNIQUE INDEX IF NOT EXISTS users_email_minuscules ON users (lower(email));

-- Qui ha creat cada compte. Un professor veu els alumnes de les seves assignatures, els dels seus grups i els que ha creat ell mateix.
ALTER TABLE users ADD COLUMN creat_per UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_users_creat_per ON users (creat_per);

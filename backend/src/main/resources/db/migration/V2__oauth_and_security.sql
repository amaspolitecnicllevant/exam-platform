-- password_hash passa a opcional (usuaris OAuth no en tenen)
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- camps per a OAuth2
ALTER TABLE users ADD COLUMN oauth_provider VARCHAR(50);
ALTER TABLE users ADD COLUMN oauth_subject  VARCHAR(255);
ALTER TABLE users ADD COLUMN enabled        BOOLEAN NOT NULL DEFAULT TRUE;

CREATE UNIQUE INDEX idx_users_oauth ON users(oauth_provider, oauth_subject)
    WHERE oauth_provider IS NOT NULL;

-- log d'auditoria mínim
CREATE TABLE audit_log (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID      REFERENCES users(id) ON DELETE SET NULL,
    action     VARCHAR(100) NOT NULL,
    resource   VARCHAR(255),
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_user ON audit_log(user_id);
CREATE INDEX idx_audit_time ON audit_log(created_at DESC);

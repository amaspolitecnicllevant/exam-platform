CREATE TABLE aules (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom        VARCHAR(100) NOT NULL UNIQUE,
    xarxa_cidr VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

ALTER TABLE exams ADD COLUMN aula_id UUID REFERENCES aules(id) ON DELETE SET NULL;

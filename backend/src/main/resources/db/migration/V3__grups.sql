CREATE TABLE grups (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(120) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE grup_students (
    grup_id    UUID NOT NULL REFERENCES grups(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (grup_id, student_id)
);

CREATE INDEX idx_grup_students_student ON grup_students(student_id);

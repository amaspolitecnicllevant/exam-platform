CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE exams (
    id           UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(255) NOT NULL,
    durada       INTEGER      NOT NULL,
    instruccions TEXT,
    status       VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    created_by   UUID         NOT NULL REFERENCES users(id),
    raw_md       TEXT         NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE questions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id         UUID         NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    ordre           INTEGER      NOT NULL,
    tipus           VARCHAR(50)  NOT NULL,
    enunciat        TEXT         NOT NULL,
    punts           NUMERIC(4,2) NOT NULL,
    model_resposta  TEXT,
    output_contains TEXT,
    output_exact    TEXT,
    output_regex    TEXT,
    test_script     TEXT,
    UNIQUE (exam_id, ordre)
);

CREATE TABLE exam_sessions (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id      UUID        NOT NULL REFERENCES exams(id),
    student_id   UUID        NOT NULL REFERENCES users(id),
    started_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    submitted_at TIMESTAMP,
    status       VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
    UNIQUE (exam_id, student_id)
);

CREATE TABLE answers (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id       UUID         NOT NULL REFERENCES exam_sessions(id) ON DELETE CASCADE,
    question_id      UUID         NOT NULL REFERENCES questions(id),
    contingut        TEXT,
    execution_output TEXT,
    auto_score       NUMERIC(4,2),
    manual_score     NUMERIC(4,2),
    corrected_at     TIMESTAMP,
    UNIQUE (session_id, question_id)
);

CREATE TABLE executions (
    id          UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    answer_id   UUID      NOT NULL REFERENCES answers(id) ON DELETE CASCADE,
    executed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    output      TEXT,
    exit_code   INTEGER,
    duration_ms BIGINT
);

CREATE INDEX idx_exams_created_by  ON exams(created_by);
CREATE INDEX idx_questions_exam    ON questions(exam_id);
CREATE INDEX idx_sessions_exam     ON exam_sessions(exam_id);
CREATE INDEX idx_sessions_student  ON exam_sessions(student_id);
CREATE INDEX idx_answers_session   ON answers(session_id);
CREATE INDEX idx_executions_answer ON executions(answer_id);

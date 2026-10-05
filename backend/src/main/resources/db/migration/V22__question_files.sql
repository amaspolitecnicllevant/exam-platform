CREATE TABLE question_files (
  id            uuid        DEFAULT gen_random_uuid() PRIMARY KEY,
  question_id   uuid        NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
  filename      varchar(255) NOT NULL,
  stored_path   varchar(500) NOT NULL,
  content_type  varchar(100),
  file_size     bigint      NOT NULL,
  created_at    timestamp   NOT NULL DEFAULT now()
);
CREATE INDEX idx_question_files_question ON question_files(question_id);

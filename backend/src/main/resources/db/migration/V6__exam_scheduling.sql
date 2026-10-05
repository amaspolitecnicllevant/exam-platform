ALTER TABLE exams
  ADD COLUMN scheduled_at      TIMESTAMP,
  ADD COLUMN scheduled_grup_id UUID REFERENCES grups(id) ON DELETE SET NULL;

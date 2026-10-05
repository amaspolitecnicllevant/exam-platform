-- modul_id nullable: grups i exàmens existents queden "orfes" fins reassignació manual
ALTER TABLE grups ADD COLUMN modul_id UUID REFERENCES moduls(id) ON DELETE SET NULL;
ALTER TABLE exams ADD COLUMN modul_id UUID REFERENCES moduls(id) ON DELETE SET NULL;

CREATE INDEX idx_grups_modul ON grups(modul_id);
CREATE INDEX idx_exams_modul ON exams(modul_id);

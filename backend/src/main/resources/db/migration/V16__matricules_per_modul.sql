-- Les matrícules passen a ser per mòdul (no per cicle)
ALTER TABLE matricules DROP CONSTRAINT IF EXISTS matricules_alumne_id_cicle_id_curs_key;
ALTER TABLE matricules DROP COLUMN cicle_id;
ALTER TABLE matricules ADD COLUMN modul_id UUID NOT NULL REFERENCES moduls(id) ON DELETE CASCADE;
ALTER TABLE matricules ADD CONSTRAINT matricules_alumne_modul_curs_key UNIQUE (alumne_id, modul_id, curs);

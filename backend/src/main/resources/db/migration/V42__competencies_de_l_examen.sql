-- Competències que declara l'examen (codis lliures: poden no existir a la programació del mòdul; llavors només es mostra un avís).
CREATE TABLE exam_competencies (
    exam_id UUID        NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    codi    VARCHAR(20) NOT NULL,
    PRIMARY KEY (exam_id, codi)
);

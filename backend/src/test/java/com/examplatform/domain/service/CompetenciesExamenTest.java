package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompetenciesExamenTest {

    private static Exam examen(String... competenciesPreguntes) {
        Exam e = Exam.builder().id(UUID.randomUUID()).questions(new ArrayList<>()).build();
        int i = 1;
        for (String c : competenciesPreguntes) {
            e.getQuestions().add(Question.builder().id(UUID.randomUUID()).exam(e).ordre(i++)
                    .tipus(QuestionType.SHORT).competencia(c).build());
        }
        return e;
    }

    @Test
    void sense_competencies_declarades_no_hi_ha_restriccio_ni_avisos() {
        Exam e = examen("CO9", null, "  ");

        assertThat(CompetenciesExamen.permeses(e)).isNull();
        assertThat(CompetenciesExamen.problemes(e)).isEmpty();
        assertThat(CompetenciesExamen.avisos(e)).isEmpty();
    }

    @Test
    void les_competencies_declarades_surten_en_ordre_natural() {
        Exam e = examen();
        e.setCompetenciesExamen(new LinkedHashSet<>(List.of("CO10", "CO2", "CO1")));

        assertThat(CompetenciesExamen.permeses(e)).containsExactly("CO1", "CO2", "CO10");
    }

    @Test
    void una_pregunta_amb_una_competencia_no_declarada_nomes_dona_un_avis() {
        Exam e = examen("CO1", "CO3", null);
        e.setCompetenciesExamen(new LinkedHashSet<>(List.of("CO1", "CO2")));

        assertThat(CompetenciesExamen.problemes(e)).containsExactly("P2: CO3");
        assertThat(CompetenciesExamen.avisos(e)).singleElement().asString().contains("P2: CO3");
    }

    @Test
    void els_problemes_surten_amb_el_numero_visible_sense_comptar_les_seccions() {
        Exam e = examen("CO1", "CO2");
        e.getQuestions().add(0, Question.builder().id(UUID.randomUUID()).exam(e).ordre(0).tipus(QuestionType.SECTION).build());
        e.setCompetenciesExamen(new LinkedHashSet<>(List.of("CO1")));

        assertThat(CompetenciesExamen.problemes(e)).containsExactly("P2: CO2");
    }

    @Test
    void amb_totes_les_competencies_declarades_no_hi_ha_avisos() {
        Exam e = examen("CO1", "CO2", null);
        e.setCompetenciesExamen(new LinkedHashSet<>(List.of("CO1", "CO2")));

        assertThat(CompetenciesExamen.avisos(e)).isEmpty();
    }
}

package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompetenciesExamenTest {

    private static Competencia co(Modul m, String codi, int ordre) {
        return Competencia.builder().id(UUID.randomUUID()).modul(m).codi(codi).ordre(ordre).build();
    }

    private static UnitatTreball ut(Modul m, String codi, Competencia... cos) {
        return UnitatTreball.builder().id(UUID.randomUUID()).modul(m).codi(codi)
                .competencies(new LinkedHashSet<>(List.of(cos))).build();
    }

    private static Exam examen(Modul modul, Set<UnitatTreball> unitats, String... competenciesPreguntes) {
        Exam e = Exam.builder().id(UUID.randomUUID()).modul(modul).unitats(unitats).questions(new ArrayList<>()).build();
        int i = 1;
        for (String c : competenciesPreguntes) {
            e.getQuestions().add(Question.builder().id(UUID.randomUUID()).exam(e).ordre(i++)
                    .tipus(QuestionType.SHORT).competencia(c).build());
        }
        return e;
    }

    private Modul modul;
    private Competencia co1, co2, co3;

    private void prepara() {
        modul = Modul.builder().id(UUID.randomUUID()).codi("0485").build();
        co1 = co(modul, "CO1", 0);
        co2 = co(modul, "CO2", 1);
        co3 = co(modul, "CO3", 2);
        modul.setCompetencies(new ArrayList<>(List.of(co1, co2, co3)));
    }

    @Test
    void sense_modul_no_hi_ha_restriccio() {
        Exam e = examen(null, new LinkedHashSet<>(), "CO9");

        assertThat(CompetenciesExamen.permeses(e)).isNull();
        CompetenciesExamen.valida(e, "QUALSEVOL");
        assertThat(CompetenciesExamen.problemes(e)).isEmpty();
    }

    @Test
    void amb_modul_sense_programacio_no_hi_ha_restriccio() {
        Modul buit = Modul.builder().id(UUID.randomUUID()).codi("0001").build();

        assertThat(CompetenciesExamen.permeses(examen(buit, new LinkedHashSet<>()))).isNull();
    }

    @Test
    void sense_ut_es_permeten_totes_les_del_modul() {
        prepara();
        Exam e = examen(modul, new LinkedHashSet<>(), "CO1", "CO3");

        assertThat(CompetenciesExamen.permeses(e)).containsExactly("CO1", "CO2", "CO3");
        assertThat(CompetenciesExamen.problemes(e)).isEmpty();
    }

    @Test
    void amb_ut_nomes_es_permeten_les_competencies_d_aquestes_ut() {
        prepara();
        Exam e = examen(modul, new LinkedHashSet<>(List.of(ut(modul, "UT01", co1, co2))), "CO1", "CO3");

        assertThat(CompetenciesExamen.permeses(e)).containsExactly("CO1", "CO2");
        assertThat(CompetenciesExamen.problemes(e)).containsExactly("P2: CO3");
        assertThatThrownBy(() -> CompetenciesExamen.valida(e, "CO3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CO3").hasMessageContaining("CO1, CO2");
        CompetenciesExamen.valida(e, "CO2");
    }

    @Test
    void la_unio_de_diverses_ut_i_la_pregunta_sense_competencia_son_valides() {
        prepara();
        Exam e = examen(modul, new LinkedHashSet<>(List.of(ut(modul, "UT01", co1), ut(modul, "UT02", co3))), "CO3", null, "  ");

        assertThat(CompetenciesExamen.permeses(e)).containsExactlyInAnyOrder("CO1", "CO3");
        assertThat(CompetenciesExamen.problemes(e)).isEmpty();
        CompetenciesExamen.valida(e, null);
        CompetenciesExamen.valida(e, "");
    }

    @Test
    void els_problemes_surten_amb_el_numero_visible_sense_comptar_les_seccions() {
        prepara();
        Exam e = examen(modul, new LinkedHashSet<>(List.of(ut(modul, "UT01", co1))), "CO1", "CO2");
        e.getQuestions().add(0, Question.builder().id(UUID.randomUUID()).exam(e).ordre(0).tipus(QuestionType.SECTION).build());

        assertThat(CompetenciesExamen.problemes(e)).containsExactly("P2: CO2");
    }
}

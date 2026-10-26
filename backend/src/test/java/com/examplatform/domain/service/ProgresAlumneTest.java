package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ProgresDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ProgresAlumneTest {

    private static final Modul SMX = Modul.builder().id(UUID.randomUUID()).nom("Seguretat").build();
    private static final Modul XAR = Modul.builder().id(UUID.randomUUID()).nom("Xarxes").build();

    private final Map<UUID, Map<UUID, Answer>> respostes = new HashMap<>();

    private static Exam examen(Modul modul, String titol, boolean publicat, String... competencies) {
        Exam e = Exam.builder().id(UUID.randomUUID()).title(titol).modul(modul).notesVisibles(publicat)
                .questions(new ArrayList<>()).build();
        int i = 1;
        for (String c : competencies) {
            e.getQuestions().add(Question.builder().id(UUID.randomUUID()).exam(e).ordre(i++).tipus(QuestionType.SHORT)
                    .enunciat("Pregunta " + (i - 1) + " de " + titol + "\nsegona línia").punts(BigDecimal.valueOf(2))
                    .competencia(c).build());
        }
        return e;
    }

    private ExamSession sessio(Exam e, int dia, double... punts) {
        ExamSession s = ExamSession.builder().id(UUID.randomUUID()).exam(e).status(SessionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.of(2026, 10, dia, 10, 0)).build();
        Map<UUID, Answer> m = new HashMap<>();
        for (int i = 0; i < punts.length; i++) {
            Question q = e.getQuestions().get(i);
            m.put(q.getId(), Answer.builder().session(s).question(q)
                    .manualScore(Double.isNaN(punts[i]) ? null : BigDecimal.valueOf(punts[i])).build());
        }
        respostes.put(s.getId(), m);
        return s;
    }

    private ProgresDto calcula(ExamSession... ss) {
        return ProgresAlumne.calcula(List.of(ss), respostes);
    }

    @Test
    void agrupa_la_mateixa_competencia_suspesa_en_diferents_examens() {
        Exam a = examen(SMX, "Examen UT01", true, "CO3", "CO3");
        Exam b = examen(SMX, "Examen UT02", true, "CO3", "CO3");

        ProgresDto r = calcula(sessio(b, 20, 0, 1), sessio(a, 10, 0, 0));

        assertThat(r.moduls()).hasSize(1);
        ProgresDto.Competencia co3 = r.moduls().get(0).competencies().get(0);
        assertThat(co3.codi()).isEqualTo("CO3");
        assertThat(co3.suspesos()).extracting(ProgresDto.Examen::titol).containsExactly("Examen UT01", "Examen UT02");
        assertThat(co3.suspesos().get(0).nota()).isEqualByComparingTo("0");
        assertThat(co3.suspesos().get(1).nota()).isEqualByComparingTo("2.50");
        assertThat(co3.notaGlobal()).isEqualByComparingTo("1.25");
    }

    @Test
    void una_competencia_aprovada_en_un_examen_no_surt_d_aquell_examen() {
        Exam a = examen(SMX, "UT01", true, "CO1", "CO2");
        Exam b = examen(SMX, "UT02", true, "CO1", "CO2");

        ProgresDto r = calcula(sessio(a, 10, 0, 2), sessio(b, 20, 2, 2));

        List<ProgresDto.Competencia> cs = r.moduls().get(0).competencies();
        assertThat(cs).extracting(ProgresDto.Competencia::codi).containsExactly("CO1");
        assertThat(cs.get(0).suspesos()).extracting(ProgresDto.Examen::titol).containsExactly("UT01");
        assertThat(cs.get(0).notaGlobal()).isEqualByComparingTo("5.00");
    }

    @Test
    void nomes_surten_les_preguntes_on_s_han_perdut_punts_amb_l_enunciat_retallat() {
        Exam a = examen(SMX, "UT01", true, "CO1", "CO1", "CO1", "CO1");

        ProgresDto r = calcula(sessio(a, 10, 0, 1, 2, 0));

        List<ProgresDto.Pregunta> ps = r.moduls().get(0).competencies().get(0).suspesos().get(0).preguntes();
        assertThat(ps).extracting(ProgresDto.Pregunta::numero).containsExactly(1, 2, 4);
        assertThat(ps.get(0).enunciat()).isEqualTo("Pregunta 1 de UT01");
        assertThat(ps.get(1).obtinguts()).isEqualByComparingTo("1");
    }

    @Test
    void els_examens_amb_les_notes_sense_publicar_no_hi_surten() {
        Exam a = examen(SMX, "UT01", false, "CO1");

        assertThat(calcula(sessio(a, 10, 0)).moduls()).isEmpty();
    }

    @Test
    void les_sessions_no_entregades_no_hi_surten() {
        Exam a = examen(SMX, "UT01", true, "CO1");
        ExamSession s = sessio(a, 10, 0);
        s.setStatus(SessionStatus.IN_PROGRESS);

        assertThat(calcula(s).moduls()).isEmpty();
    }

    @Test
    void una_competencia_amb_preguntes_pendents_en_un_examen_no_hi_compta() {
        Exam a = examen(SMX, "UT01", true, "CO1", "CO1");

        assertThat(calcula(sessio(a, 10, 0, Double.NaN)).moduls()).isEmpty();
    }

    @Test
    void els_moduls_no_es_barregen() {
        Exam a = examen(SMX, "UT01", true, "CO1");
        Exam b = examen(XAR, "Xarxes 1", true, "CO1");

        ProgresDto r = calcula(sessio(a, 10, 0), sessio(b, 11, 0));

        assertThat(r.moduls()).extracting(ProgresDto.Modul::modulNom).containsExactly("Seguretat", "Xarxes");
        assertThat(r.moduls()).allSatisfy(m -> assertThat(m.competencies().get(0).suspesos()).hasSize(1));
    }

    @Test
    void les_preguntes_sense_competencia_i_els_bonus_no_compten_com_a_suspeses() {
        Exam a = examen(SMX, "UT01", true, "CO1", null, "CO1");
        a.getQuestions().get(2).setAnulada(true);

        // CO1: P1 = 2/2 i P3 (bonus) = 2/2 → aprovada; la P2 no té competència
        assertThat(calcula(sessio(a, 10, 2, 0, 0)).moduls()).isEmpty();
    }

    @Test
    void els_codis_es_comparen_sense_distingir_majuscules_i_s_ordenen_de_manera_natural() {
        Exam a = examen(SMX, "UT01", true, "co10", "CO2", "CO10");

        ProgresDto r = calcula(sessio(a, 10, 0, 0, 0));

        assertThat(r.moduls().get(0).competencies()).extracting(ProgresDto.Competencia::codi).containsExactly("CO2", "CO10");
        assertThat(r.moduls().get(0).competencies().get(1).suspesos().get(0).preguntes()).hasSize(2);
    }

    @Test
    void sense_cap_suspens_el_progres_es_buit() {
        Exam a = examen(SMX, "UT01", true, "CO1");

        assertThat(calcula(sessio(a, 10, 2)).moduls()).isEmpty();
        assertThat(calcula().moduls()).isEmpty();
    }

    @Test
    void l_enunciat_llarg_es_retalla() {
        assertThat(ProgresAlumne.resumEnunciat("x".repeat(500))).hasSize(ProgresAlumne.MAX_ENUNCIAT).endsWith("…");
        assertThat(ProgresAlumne.resumEnunciat(null)).isEmpty();
    }
}

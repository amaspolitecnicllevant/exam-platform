package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ResultatsModulDto;
import com.examplatform.domain.service.ResultatsModul.ExamenEntregat;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ResultatsModulTest {

    private final Modul modul = Modul.builder().id(UUID.randomUUID()).codi("0485").build();
    private final Competencia co1 = Competencia.builder().id(UUID.randomUUID()).modul(modul).codi("CO1").descripcio("Instal·la").ordre(0).build();
    private final Competencia co2 = Competencia.builder().id(UUID.randomUUID()).modul(modul).codi("CO2").descripcio("Gestiona").ordre(1).build();
    private final UnitatTreball ut1 = UnitatTreball.builder().id(UUID.randomUUID()).modul(modul).codi("UT01").nom("Instal·lació").ordre(0)
            .competencies(new LinkedHashSet<>(List.of(co1, co2))).build();
    private final UnitatTreball ut2 = UnitatTreball.builder().id(UUID.randomUUID()).modul(modul).codi("UT02").nom("Usuaris").ordre(1)
            .competencies(new LinkedHashSet<>(List.of(co2))).build();

    ResultatsModulTest() {
        modul.setCompetencies(new ArrayList<>(List.of(co1, co2)));
    }

    private Exam examen(String titol, UnitatTreball... uts) {
        return Exam.builder().id(UUID.randomUUID()).title(titol).questions(new ArrayList<>())
                .unitats(new LinkedHashSet<>(List.of(uts))).build();
    }

    private Question pregunta(Exam e, String punts, String co) {
        Question q = Question.builder().id(UUID.randomUUID()).exam(e).ordre(e.getQuestions().size() + 1)
                .tipus(QuestionType.SHORT).punts(new BigDecimal(punts)).competencia(co).build();
        e.getQuestions().add(q);
        return q;
    }

    /** Un alumne entregat amb la nota (manual) de cada pregunta. */
    private Map<UUID, Answer> alumne(Map<Question, String> notes) {
        Map<UUID, Answer> res = new HashMap<>();
        notes.forEach((q, n) -> res.put(q.getId(), Answer.builder().id(UUID.randomUUID()).question(q)
                .contingut("x").manualScore(n == null ? null : new BigDecimal(n)).build()));
        return res;
    }

    @Test
    void la_unitat_agrupa_els_seus_examens_amb_punts_reals_i_el_desglossament_per_competencia() {
        Exam a = examen("Examen UT01", ut1);       // 10 punts: CO1 4, CO2 6
        Question a1 = pregunta(a, "4", "CO1"), a2 = pregunta(a, "6", "CO2");
        Exam b = examen("Pràctica UT01", ut1);     // 10 punts: CO2 10
        Question b1 = pregunta(b, "10", "CO2");
        List<ExamenEntregat> examens = List.of(
                new ExamenEntregat(a, List.of(alumne(Map.of(a1, "4", a2, "6")), alumne(Map.of(a1, "0", a2, "2")))),   // mitjana 6: CO1 2, CO2 4
                new ExamenEntregat(b, List.of(alumne(Map.of(b1, "8")))));                                              // mitjana 8: CO2 8

        ResultatsModulDto r = ResultatsModul.calcula(modul, List.of(ut1, ut2), examens);

        ResultatsModulDto.Unitat u1 = r.unitats().get(0);
        assertThat(u1.codi()).isEqualTo("UT01");
        assertThat(u1.nota().mitjanaPunts()).isEqualByComparingTo("14");        // 6 + 8
        assertThat(u1.nota().maxim()).isEqualByComparingTo("20");
        assertThat(u1.nota().sobreDeu()).isEqualByComparingTo("7");
        assertThat(u1.examens()).extracting(ResultatsModulDto.Examen::titol).containsExactly("Examen UT01", "Pràctica UT01");
        assertThat(u1.examens().get(0).entregats()).isEqualTo(2);
        assertThat(u1.examens().get(0).nota().mitjanaPunts()).isEqualByComparingTo("6");
        assertThat(u1.competencies()).extracting(ResultatsModulDto.CompetenciaDeUnitat::codi).containsExactly("CO1", "CO2");
        assertThat(u1.competencies().get(0).nota().mitjanaPunts()).isEqualByComparingTo("2");   // CO1: 2 de 4
        assertThat(u1.competencies().get(1).nota().mitjanaPunts()).isEqualByComparingTo("12");   // CO2: 4 + 8 de 16
        assertThat(u1.competencies().get(1).nota().maxim()).isEqualByComparingTo("16");
        // La unitat sense exàmens no té nota
        assertThat(r.unitats().get(1).nota()).isNull();
        assertThat(r.unitats().get(1).examens()).isEmpty();
    }

    @Test
    void la_competencia_agrupa_tots_els_examens_i_es_desglossa_per_unitat_i_examen_amb_rendiment_i_pes() {
        Exam a = examen("Examen UT01", ut1);
        Question a1 = pregunta(a, "4", "CO1"), a2 = pregunta(a, "6", "CO2");
        Exam b = examen("Pràctica UT01", ut1);
        Question b1 = pregunta(b, "10", "CO2");
        Exam c = examen("Examen UT02", ut2);
        Question c1 = pregunta(c, "10", "CO2");
        List<ExamenEntregat> examens = List.of(
                new ExamenEntregat(a, List.of(alumne(Map.of(a1, "4", a2, "3")))),
                new ExamenEntregat(b, List.of(alumne(Map.of(b1, "5")))),
                new ExamenEntregat(c, List.of(alumne(Map.of(c1, "9")))));

        ResultatsModulDto r = ResultatsModul.calcula(modul, List.of(ut1, ut2), examens);

        ResultatsModulDto.Competencia r1 = r.competencies().get(0), r2 = r.competencies().get(1);
        assertThat(r1.codi()).isEqualTo("CO1");
        assertThat(r1.nota().sobreDeu()).isEqualByComparingTo("10");             // 4 de 4
        assertThat(r2.codi()).isEqualTo("CO2");
        assertThat(r2.nota().mitjanaPunts()).isEqualByComparingTo("17");          // 3 + 5 + 9
        assertThat(r2.nota().maxim()).isEqualByComparingTo("26");
        assertThat(r2.nota().sobreDeu()).isEqualByComparingTo("6.54");            // 17 * 10 / 26
        assertThat(r2.perUnitat()).extracting(ResultatsModulDto.UnitatDeCompetencia::codi).containsExactly("UT01", "UT02");
        List<ResultatsModulDto.ExamenDeCompetencia> ut1Exams = r2.perUnitat().get(0).examens();
        assertThat(ut1Exams).extracting(ResultatsModulDto.ExamenDeCompetencia::titol).containsExactly("Examen UT01", "Pràctica UT01");
        assertThat(ut1Exams.get(0).percentRendiment()).isEqualByComparingTo("50");     // 3 de 6
        assertThat(ut1Exams.get(0).percentPes()).isEqualByComparingTo("37.5");         // 6 de 16 punts de CO2 a la UT01
        assertThat(ut1Exams.get(1).percentRendiment()).isEqualByComparingTo("50");     // 5 de 10
        assertThat(ut1Exams.get(1).percentPes()).isEqualByComparingTo("62.5");         // 10 de 16
        assertThat(r2.perUnitat().get(1).examens().get(0).percentPes()).isEqualByComparingTo("100");
    }

    @Test
    void els_examens_sense_alumnes_entregats_no_compten_i_els_sense_unitat_surten_a_part() {
        Exam a = examen("Sense entregues", ut1);
        pregunta(a, "10", "CO1");
        Exam orfe = examen("Sense unitat");
        Question o1 = pregunta(orfe, "10", "CO1");

        ResultatsModulDto r = ResultatsModul.calcula(modul, List.of(ut1),
                List.of(new ExamenEntregat(a, List.of()), new ExamenEntregat(orfe, List.of(alumne(Map.of(o1, "7"))))));

        assertThat(r.examens()).isEqualTo(1);
        assertThat(r.unitats().get(0).nota()).isNull();
        ResultatsModulDto.Competencia co = r.competencies().get(0);
        assertThat(co.nota().sobreDeu()).isEqualByComparingTo("7");
        assertThat(co.perUnitat()).singleElement().satisfies(u -> {
            assertThat(u.codi()).isNull();
            assertThat(u.nom()).contains("sense unitat");
        });
        assertThat(r.competencies().get(1).nota()).isNull();      // CO2: cap examen la porta
    }

    @Test
    void les_respostes_sense_nota_compten_zero_i_es_compten_com_a_pendents() {
        Exam a = examen("Examen UT01", ut1);
        Question a1 = pregunta(a, "10", "CO1");

        ResultatsModulDto r = ResultatsModul.calcula(modul, List.of(ut1),
                List.of(new ExamenEntregat(a, List.of(alumne(Map.of(a1, "6")), alumne(new HashMap<>(Collections.singletonMap(a1, null)))))));

        assertThat(r.respostesPendents()).isEqualTo(1);
        assertThat(r.unitats().get(0).nota().mitjanaPunts()).isEqualByComparingTo("3");   // (6 + 0) / 2
    }

    // ── exàmens i pràctiques amb percentatge per unitat ──────────────────────

    private Exam practica(String titol, UnitatTreball... uts) {
        Exam e = examen(titol, uts);
        e.setTipus(TipusExamen.PRACTICA);
        return e;
    }

    @Test
    void amb_percentatge_definit_la_unitat_pondera_els_examens_i_les_practiques() {
        ut1.setPesExamens(70);
        Exam a1 = examen("Examen 1", ut1);        // 10 punts, mitjana 6
        Question q1 = pregunta(a1, "10", "CO1");
        Exam a2 = examen("Examen 2", ut1);        // 20 punts, mitjana 18
        Question q2 = pregunta(a2, "20", "CO1");
        Exam p = practica("Pràctica 1", ut1);     // 10 punts, mitjana 9
        Question q3 = pregunta(p, "10", "CO1");

        ResultatsModulDto r = ResultatsModul.calcula(modul, List.of(ut1), List.of(
                new ExamenEntregat(a1, List.of(alumne(Map.of(q1, "6")))),
                new ExamenEntregat(a2, List.of(alumne(Map.of(q2, "18")))),
                new ExamenEntregat(p, List.of(alumne(Map.of(q3, "9"))))));

        ResultatsModulDto.Unitat u = r.unitats().get(0);
        assertThat(u.pesExamens()).isEqualTo(70);
        assertThat(u.notaExamens().sobreDeu()).isEqualByComparingTo("8");           // (6 + 18) / 30
        assertThat(u.notaPractiques().sobreDeu()).isEqualByComparingTo("9");
        assertThat(u.nota().sobreDeu()).isEqualByComparingTo("8.3");                // 0,7 · 8 + 0,3 · 9
        assertThat(u.nota().maxim()).isEqualByComparingTo("10");
        assertThat(u.examens()).extracting(ResultatsModulDto.Examen::tipus)
                .containsExactly(TipusExamen.EXAMEN, TipusExamen.EXAMEN, TipusExamen.PRACTICA);
        assertThat(u.examens().get(0).percentPes()).isEqualByComparingTo("23.3");   // 70 · 10/30
        assertThat(u.examens().get(1).percentPes()).isEqualByComparingTo("46.7");   // 70 · 20/30
        assertThat(u.examens().get(2).percentPes()).isEqualByComparingTo("30");
        // La competència segueix els mateixos percentatges
        ResultatsModulDto.Competencia co = r.competencies().get(0);
        assertThat(co.nota().sobreDeu()).isEqualByComparingTo("8.3");
        assertThat(co.perUnitat().get(0).examens().get(2).percentPes()).isEqualByComparingTo("30");
        assertThat(u.competencies().get(0).nota().sobreDeu()).isEqualByComparingTo("8.3");
    }

    @Test
    void si_nomes_hi_ha_una_part_aquesta_compta_el_cent_per_cent() {
        ut1.setPesExamens(70);
        Exam a = examen("Examen", ut1);
        Question q = pregunta(a, "10", "CO1");

        ResultatsModulDto r = ResultatsModul.calcula(modul, List.of(ut1),
                List.of(new ExamenEntregat(a, List.of(alumne(Map.of(q, "6"))))));

        assertThat(r.unitats().get(0).nota().sobreDeu()).isEqualByComparingTo("6");
        assertThat(r.unitats().get(0).notaPractiques()).isNull();
        assertThat(r.unitats().get(0).examens().get(0).percentPes()).isEqualByComparingTo("100");
    }

    @Test
    void sense_percentatge_exàmens_i_practiques_compten_junts_pels_punts() {
        Exam a = examen("Examen", ut1);
        Question q = pregunta(a, "10", "CO1");
        Exam p = practica("Pràctica", ut1);
        Question q2 = pregunta(p, "10", "CO1");

        ResultatsModulDto r = ResultatsModul.calcula(modul, List.of(ut1), List.of(
                new ExamenEntregat(a, List.of(alumne(Map.of(q, "4")))),
                new ExamenEntregat(p, List.of(alumne(Map.of(q2, "8"))))));

        assertThat(r.unitats().get(0).pesExamens()).isNull();
        assertThat(r.unitats().get(0).nota().sobreDeu()).isEqualByComparingTo("6");   // (4 + 8) / 20
        assertThat(r.unitats().get(0).examens().get(0).percentPes()).isEqualByComparingTo("50");
    }

    @Test
    void una_competencia_en_dues_unitats_amb_percentatges_diferents_es_pondera_pels_seus_punts() {
        ut1.setPesExamens(100);                      // UT01: tot examen
        ut2.setPesExamens(0);                        // UT02: tot pràctica
        Exam a = examen("Examen UT01", ut1);
        Question q1 = pregunta(a, "10", "CO2");
        Exam p = practica("Pràctica UT01", ut1);
        Question q2 = pregunta(p, "10", "CO2");      // no compta a la UT01 (pes 0 de pràctiques)
        Exam p2 = practica("Pràctica UT02", ut2);
        Question q3 = pregunta(p2, "30", "CO2");

        ResultatsModulDto r = ResultatsModul.calcula(modul, List.of(ut1, ut2), List.of(
                new ExamenEntregat(a, List.of(alumne(Map.of(q1, "5")))),               // 5/10
                new ExamenEntregat(p, List.of(alumne(Map.of(q2, "10")))),              // 10/10 (pes 0)
                new ExamenEntregat(p2, List.of(alumne(Map.of(q3, "21"))))));           // 7/10

        ResultatsModulDto.Competencia co2 = r.competencies().get(1);
        assertThat(r.unitats().get(0).nota().sobreDeu()).isEqualByComparingTo("5");     // només l'examen
        assertThat(r.unitats().get(1).nota().sobreDeu()).isEqualByComparingTo("7");     // només la pràctica
        // UT01 pesa 20 punts de CO2 i UT02 30: (5·20 + 7·30) / 50 = 6,2
        assertThat(co2.nota().sobreDeu()).isEqualByComparingTo("6.2");
        assertThat(co2.perUnitat().get(0).examens().get(1).percentPes()).isEqualByComparingTo("0");
    }
}

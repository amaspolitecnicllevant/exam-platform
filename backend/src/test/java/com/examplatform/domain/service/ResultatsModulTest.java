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
}

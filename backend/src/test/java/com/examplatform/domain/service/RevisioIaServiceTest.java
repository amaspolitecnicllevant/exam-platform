package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.domain.service.exportacio.ExportacioService;
import com.examplatform.domain.service.exportacio.FixtureExamen;
import com.examplatform.dto.RevisioIaDto;
import com.examplatform.dto.RevisioIaDto.*;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RevisioIaServiceTest {

    @Mock ExportacioService exportacio;
    @Mock AnswerRepository  answerRepository;
    @Mock AuditLogService   auditLog;

    RevisioIaService service;
    User professor;
    FixtureExamen f;

    @BeforeEach
    void setUp() {
        service = new RevisioIaService(exportacio, answerRepository, auditLog);
        professor = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
        f = new FixtureExamen();
    }

    private void dades() {
        when(exportacio.dades(f.exam.getId(), professor)).thenReturn(f.dades());
    }

    private String codi(ExamSession s) {
        return f.dades().codisAnonims().get(s.getId());
    }

    private RevisioIaDto previsualitza(String text) {
        dades();
        return service.previsualitza(f.exam.getId(), text, professor);
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    // ── Previsualització: què canvia ──────────────────────────────────────────

    @Test
    void una_nota_diferent_d_una_resposta_ja_revisada_es_un_canvi_que_sobreescriu_la_revisada() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, p, "resposta", "1");

        RevisioIaDto r = previsualitza(codi(s) + ";1;1.5;Millor del que semblava");

        assertThat(r.canvis()).isEqualTo(1);
        Fila fila = r.files().get(0);
        assertThat(fila.estat()).isEqualTo(Estat.CANVI);
        assertThat(fila.notaActual()).isEqualByComparingTo("1");
        assertThat(fila.origenActual()).isEqualTo(Origen.REVISADA);
        assertThat(fila.notaNova()).isEqualByComparingTo("1.5");
        assertThat(fila.sobreescriuRevisada()).isTrue();
        assertThat(fila.puntsMax()).isEqualByComparingTo("2");
        assertThat(fila.alumne()).isEqualTo("Anna");
        assertThat(fila.justificacio()).isEqualTo("Millor del que semblava");
    }

    @Test
    void sobre_una_proposta_automatica_no_es_marca_com_a_sobreescriptura() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, p, "resposta", null).setAutoScore(bd("0.5"));

        Fila fila = previsualitza(codi(s) + ";1;2;x").files().get(0);

        assertThat(fila.estat()).isEqualTo(Estat.CANVI);
        assertThat(fila.origenActual()).isEqualTo(Origen.PROPOSTA);
        assertThat(fila.sobreescriuRevisada()).isFalse();
    }

    @Test
    void sense_cap_nota_prèvia_l_origen_es_cap() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, p, "resposta", null);

        Fila fila = previsualitza(codi(s) + ";1;1;x").files().get(0);

        assertThat(fila.origenActual()).isEqualTo(Origen.CAP);
        assertThat(fila.notaActual()).isNull();
        assertThat(fila.estat()).isEqualTo(Estat.CANVI);
    }

    @Test
    void la_mateixa_nota_que_la_revisada_no_es_cap_canvi() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, p, "resposta", "1.5");

        RevisioIaDto r = previsualitza(codi(s) + ";1;1,5;x");

        assertThat(r.iguals()).isEqualTo(1);
        assertThat(r.canvis()).isZero();
        assertThat(r.files().get(0).estat()).isEqualTo(Estat.IGUAL);
    }

    @Test
    void coincidir_amb_la_proposta_automatica_passa_a_revisada_i_ho_diu() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, p, "resposta", null).setAutoScore(bd("1"));

        Fila fila = previsualitza(codi(s) + ";1;1;x").files().get(0);

        assertThat(fila.estat()).isEqualTo(Estat.CANVI);
        assertThat(fila.motiu()).contains("proposta automàtica");
    }

    // ── Identificació de l'alumne i la pregunta ───────────────────────────────

    @Test
    void identifica_l_alumne_pel_codi_amb_o_sense_prefix_pel_correu_i_pel_nom() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession a = f.entregat("Anna Soler", "anna@x.cat");
        ExamSession b = f.entregat("Berta Pons", "berta@x.cat");
        ExamSession c = f.entregat("Carla Roca", "carla@x.cat");
        ExamSession d = f.entregat("Dani Gil", "dani@x.cat");
        for (ExamSession s : List.of(a, b, c, d)) f.resposta(s, p, "r", "0");
        String codiA = codi(a);

        RevisioIaDto r = previsualitza(String.join("\n",
                codiA + ";1;1;x",
                codi(b).replace("Alumne ", "").toLowerCase() + ";1;1;x",
                "Carla Roca <CARLA@x.cat>;1;1;x",
                "dani gil;1;1;x"));

        assertThat(r.errors()).isZero();
        assertThat(r.files()).extracting(Fila::alumne).containsExactly("Anna Soler", "Berta Pons", "Carla Roca", "Dani Gil");
    }

    @Test
    void un_alumne_desconegut_o_un_nom_repetit_es_un_error() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        f.resposta(f.entregat("Joan Pons", "joan1@x.cat"), p, "r", "0");
        f.resposta(f.entregat("Joan Pons", "joan2@x.cat"), p, "r", "0");

        RevisioIaDto r = previsualitza("Alumne FFFFFF;1;1;x\nJoan Pons;1;1;x");

        assertThat(r.errors()).isEqualTo(2);
        assertThat(r.files()).allSatisfy(fila -> assertThat(fila.estat()).isEqualTo(Estat.ERROR));
        assertThat(r.files().get(0).motiu()).contains("Alumne no reconegut");
    }

    @Test
    void una_pregunta_inexistent_es_un_error() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, p, "r", "0");

        Fila fila = previsualitza(codi(s) + ";9;1;x").files().get(0);

        assertThat(fila.estat()).isEqualTo(Estat.ERROR);
        assertThat(fila.motiu()).contains("no existeix");
    }

    @Test
    void la_pregunta_pot_venir_com_p3_o_amb_text() {
        f.pregunta(QuestionType.SHORT, "2", null);
        f.pregunta(QuestionType.SHORT, "2", null);
        Question p3 = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, p3, "r", "0");

        Fila fila = previsualitza(codi(s) + ";P3 · Resposta curta;1;x").files().get(0);

        assertThat(fila.estat()).isEqualTo(Estat.CANVI);
        assertThat(fila.pregunta()).isEqualTo(3);
    }

    // ── Què no es pot canviar ─────────────────────────────────────────────────

    @Test
    void les_preguntes_de_test_els_lliuraments_i_les_anulades_s_ignoren() {
        Question test = f.test("1", null);
        Question fitxer = f.pregunta(QuestionType.FILE_UPLOAD, "1", null);
        Question anulada = f.pregunta(QuestionType.SHORT, "1", null);
        anulada.setAnulada(true);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, test, "a", "0");
        f.resposta(s, fitxer, "x", "0");
        f.resposta(s, anulada, "r", "0");
        String c = codi(s);

        RevisioIaDto r = previsualitza(c + ";1;1;x\n" + c + ";2;1;x\n" + c + ";3;1;x");

        assertThat(r.ignorades()).isEqualTo(3);
        assertThat(r.canvis()).isZero();
        assertThat(r.files()).extracting(Fila::motiu).anyMatch(m -> m.contains("test"))
                .anyMatch(m -> m.contains("fitxer")).anyMatch(m -> m.contains("anul·lada"));
        assertThat(r.files()).allSatisfy(fila -> assertThat(fila.answerId()).isNull());
    }

    @Test
    void un_alumne_que_no_ha_entregat_o_una_resposta_buida_s_ignoren() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession enCurs = f.alumne("Berta", "berta@x.cat", SessionStatus.IN_PROGRESS, true);
        f.resposta(enCurs, p, "r", null);
        ExamSession buit = f.entregat("Carla", "carla@x.cat");
        f.resposta(buit, p, "   ", null);
        ExamSession senseRes = f.entregat("Dani", "dani@x.cat");

        RevisioIaDto r = previsualitza(String.join("\n", codi(enCurs) + ";1;1;x", codi(buit) + ";1;1;x", codi(senseRes) + ";1;1;x"));

        assertThat(r.ignorades()).isEqualTo(3);
        assertThat(r.files().get(0).motiu()).contains("no ha entregat");
        assertThat(r.files().get(1).motiu()).contains("Sense resposta");
        assertThat(r.files().get(2).motiu()).contains("Sense resposta");
    }

    // ── Notes invàlides ───────────────────────────────────────────────────────

    @Test
    void una_nota_fora_de_rang_o_il_legible_es_un_error_i_mai_se_retalla() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, p, "r", "1");
        String c = codi(s);

        RevisioIaDto negativa = previsualitza(c + ";1;-1;x");
        RevisioIaDto massa = previsualitza(c + ";1;2.5;x");
        RevisioIaDto text = previsualitza(c + ";1;molt bé;x");

        assertThat(negativa.files().get(0).motiu()).contains("fora de rang");
        assertThat(massa.files().get(0).motiu()).contains("entre 0 i 2");
        assertThat(text.files().get(0).motiu()).contains("Nota no vàlida");
        assertThat(List.of(negativa, massa, text)).allSatisfy(r -> assertThat(r.errors()).isEqualTo(1));
    }

    @Test
    void les_notes_amb_format_lliure_s_entenen_i_s_arrodoneixen_a_dos_decimals() {
        assertThat(RevisioIaService.nota("7,5")).isEqualByComparingTo("7.5");
        assertThat(RevisioIaService.nota("**7.5**")).isEqualByComparingTo("7.5");
        assertThat(RevisioIaService.nota("1,5/2")).isEqualByComparingTo("1.5");
        assertThat(RevisioIaService.nota("0.333")).isEqualByComparingTo("0.33");
        assertThat(RevisioIaService.nota("0")).isEqualByComparingTo("0");
        assertThat(RevisioIaService.nota("—")).isNull();
        assertThat(RevisioIaService.nota("")).isNull();
        assertThat(RevisioIaService.nota(null)).isNull();
    }

    @Test
    void una_fila_repetida_per_al_mateix_alumne_i_pregunta_es_error_a_totes_dues() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, p, "r", "0");
        String c = codi(s);

        RevisioIaDto r = previsualitza(c + ";1;1;x\n" + c + ";1;2;y");

        assertThat(r.errors()).isEqualTo(2);
        assertThat(r.canvis()).isZero();
    }

    @Test
    void un_fitxer_sense_cap_fila_dona_un_avis() {
        f.pregunta(QuestionType.SHORT, "2", null);

        RevisioIaDto r = previsualitza("Hola, no he pogut fer-ho.");

        assertThat(r.files()).isEmpty();
        assertThat(r.avisos()).anyMatch(a -> a.contains("No s'ha trobat cap fila"));
    }

    // ── Resum per alumne i notes publicades ───────────────────────────────────

    @Test
    void el_resum_per_alumne_dona_la_nota_sobre_10_abans_i_despres() {
        Question p1 = f.pregunta(QuestionType.SHORT, "5", null);
        Question p2 = f.pregunta(QuestionType.SHORT, "5", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        f.resposta(s, p1, "r", "2");
        f.resposta(s, p2, "r", "3");

        RevisioIaDto r = previsualitza(codi(s) + ";1;4;x");

        assertThat(r.alumnes()).hasSize(1);
        assertThat(r.alumnes().get(0).notaAbans()).isEqualByComparingTo("5");     // 2 + 3 de 10 punts
        assertThat(r.alumnes().get(0).notaDespres()).isEqualByComparingTo("7");   // 4 + 3 de 10 punts
    }

    @Test
    void les_notes_publicades_es_marquen() {
        f.pregunta(QuestionType.SHORT, "2", null);
        f.exam.setNotesVisibles(true);

        assertThat(previsualitza("").notesPublicades()).isTrue();
    }

    @Test
    void previsualitzar_no_desa_res_ni_deixa_rastre() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        Answer a = f.resposta(s, p, "r", "1");

        previsualitza(codi(s) + ";1;2;x");

        verifyNoInteractions(answerRepository, auditLog);
        assertThat(a.getManualScore()).isEqualByComparingTo("1");
        assertThat(a.getRevisioIaEl()).isNull();
    }

    // ── Aplicació ─────────────────────────────────────────────────────────────

    @Test
    void aplicar_desa_la_nota_com_a_revisada_amb_la_nota_anterior_i_la_justificacio() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        Answer a = f.resposta(s, p, "r", null);
        a.setAutoScore(bd("0.5"));
        dades();

        Aplicacio r = service.aplica(f.exam.getId(), codi(s) + ";1;1.5;Molt bé",
                List.of(new Acceptada(a.getId(), bd("0.5"))), professor);

        assertThat(r.aplicades()).isEqualTo(1);
        assertThat(r.saltades()).isZero();
        assertThat(a.getManualScore()).isEqualByComparingTo("1.5");
        assertThat(a.getRevisioIaNotaAbans()).isEqualByComparingTo("0.5");
        assertThat(a.getRevisioIaJustificacio()).isEqualTo("Molt bé");
        assertThat(a.getRevisioIaEl()).isNotNull();
        assertThat(a.getCorrectedAt()).isNotNull();
        assertThat(a.getComentari()).isNull();   // la justificació de la IA no va al comentari de l'alumne
        verify(answerRepository).save(a);
        verify(auditLog).log(eq(professor.getId()), eq("EXAM_REVISIO_IA"), contains("1 notes aplicades"));
    }

    @Test
    void aplicar_nomes_toca_les_files_acceptades() {
        Question p1 = f.pregunta(QuestionType.SHORT, "2", null);
        Question p2 = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        Answer a1 = f.resposta(s, p1, "r", "0");
        Answer a2 = f.resposta(s, p2, "r", "0");
        dades();
        String c = codi(s);

        service.aplica(f.exam.getId(), c + ";1;1;x\n" + c + ";2;2;y", List.of(new Acceptada(a2.getId(), bd("0"))), professor);

        assertThat(a1.getManualScore()).isEqualByComparingTo("0");
        assertThat(a1.getRevisioIaEl()).isNull();
        assertThat(a2.getManualScore()).isEqualByComparingTo("2");
        verify(answerRepository, never()).save(a1);
    }

    @Test
    void si_la_nota_ha_canviat_des_de_la_previsualitzacio_la_fila_se_salta() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        Answer a = f.resposta(s, p, "r", "1.5");   // ara val 1.5
        dades();

        Aplicacio r = service.aplica(f.exam.getId(), codi(s) + ";1;2;x", List.of(new Acceptada(a.getId(), bd("1"))), professor);   // el professor va veure 1

        assertThat(r.aplicades()).isZero();
        assertThat(r.saltades()).isEqualTo(1);
        assertThat(r.motius()).anyMatch(m -> m.contains("ha canviat des de la previsualització"));
        assertThat(a.getManualScore()).isEqualByComparingTo("1.5");
        verify(answerRepository, never()).save(any());
    }

    @Test
    void una_resposta_que_no_surt_al_fitxer_o_no_es_un_canvi_no_s_aplica() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        Answer a = f.resposta(s, p, "r", "1");
        dades();

        Aplicacio r = service.aplica(f.exam.getId(), codi(s) + ";1;1;igual",
                List.of(new Acceptada(a.getId(), bd("1")), new Acceptada(UUID.randomUUID(), null)), professor);

        assertThat(r.aplicades()).isZero();
        assertThat(r.saltades()).isEqualTo(2);
        verify(answerRepository, never()).save(any());
    }

    @Test
    void una_mateixa_resposta_acceptada_dues_vegades_nomes_s_aplica_una_cop() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        Answer a = f.resposta(s, p, "r", "0");
        dades();

        Aplicacio r = service.aplica(f.exam.getId(), codi(s) + ";1;1;x",
                List.of(new Acceptada(a.getId(), bd("0")), new Acceptada(a.getId(), bd("0"))), professor);

        assertThat(r.aplicades()).isEqualTo(1);
        verify(answerRepository, times(1)).save(a);
    }

    @Test
    void una_justificacio_massa_llarga_es_retalla() {
        Question p = f.pregunta(QuestionType.SHORT, "2", null);
        ExamSession s = f.entregat("Anna", "anna@x.cat");
        Answer a = f.resposta(s, p, "r", "0");
        dades();

        service.aplica(f.exam.getId(), codi(s) + ";1;1;" + "a".repeat(5000), List.of(new Acceptada(a.getId(), bd("0"))), professor);

        assertThat(a.getRevisioIaJustificacio()).hasSize(RevisioIaService.MAX_JUSTIFICACIO);
    }

    @Test
    void sense_permis_sobre_l_examen_no_es_previsualitza_ni_s_aplica_res() {
        f.pregunta(QuestionType.SHORT, "2", null);
        when(exportacio.dades(f.exam.getId(), professor)).thenThrow(new AccessDeniedException("no"));

        assertThatThrownBy(() -> service.previsualitza(f.exam.getId(), "x", professor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.aplica(f.exam.getId(), "x", List.of(), professor)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(answerRepository, auditLog);
    }
}

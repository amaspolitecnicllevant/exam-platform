package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.domain.port.ExamParser;
import com.examplatform.dto.ExamDto;
import com.examplatform.dto.ExamSettingsRequest;
import com.examplatform.dto.QuestionPatchRequest;
import com.examplatform.dto.ScheduleRequest;
import com.examplatform.infrastructure.persistence.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    @Mock ExamRepository          examRepository;
    @Mock ExamParser              examParser;
    @Mock GrupRepository          grupRepository;
    @Mock QuestionRepository      questionRepository;
    @Mock ModulRepository         modulRepository;
    @Mock ImparticioRepository    imparticioRepository;
    @Mock AulaRepository          aulaRepository;
    @Mock ExamSessionRepository   sessionRepository;
    @Mock QuestionFileRepository  questionFileRepository;
    @Mock com.examplatform.infrastructure.persistence.AnswerRepository answerRepository;
    @Mock com.examplatform.infrastructure.storage.FitxersRespostaStorage fitxersStorage;
    @Mock AudienciaExamenService audiencia;

    ExamService service;

    User professor;
    User altreProf;
    User admin;

    @BeforeEach
    void setUp() {
        service = new ExamService(examRepository, examParser, grupRepository,
                questionRepository, modulRepository, imparticioRepository, aulaRepository,
                sessionRepository, questionFileRepository, answerRepository, fitxersStorage, audiencia);
        professor = user(Role.PROFESSOR);
        altreProf = user(Role.PROFESSOR);
        admin     = user(Role.ADMIN);
    }

    // ── publish ───────────────────────────────────────────────────────────────

    @Test
    void publish_draft_canvia_a_published() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.publish(exam.getId(), professor);

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
    }

    @Test
    void publish_amb_punts_que_no_sumen_10_es_rebutja_i_les_seccions_no_compten() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        exam.getQuestions().get(0).setPunts(new BigDecimal("7.5"));
        exam.getQuestions().add(pregunta(exam, 2, QuestionType.SECTION, "0"));
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.publish(exam.getId(), professor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("7.5").hasMessageContaining("10");
        assertThat(exam.getStatus()).isEqualTo(ExamStatus.DRAFT);

        exam.getQuestions().add(pregunta(exam, 3, QuestionType.SHORT, "2.5"));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.publish(exam.getId(), professor);
        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
    }

    @Test
    void publish_per_a_alumnes_concrets_restringeix_l_examen_i_els_assigna() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        List<UUID> alumnes = List.of(UUID.randomUUID(), UUID.randomUUID());

        service.publish(exam.getId(), professor, alumnes);

        assertThat(exam.isRestringit()).isTrue();
        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
        verify(audiencia).assigna(exam, alumnes);
    }

    @Test
    void publish_sense_destinataris_es_per_a_tots_i_no_assigna_ningu() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.publish(exam.getId(), professor);

        assertThat(exam.isRestringit()).isFalse();
        verifyNoInteractions(audiencia);
    }

    @Test
    void publish_conserva_els_destinataris_triats_a_l_esborrany() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        exam.setRestringit(true);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.existsByExamId(exam.getId())).thenReturn(true);

        service.publish(exam.getId(), professor);

        assertThat(exam.isRestringit()).isTrue();
        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
    }

    @Test
    void publish_restringit_sense_cap_alumne_es_rebutja_perque_no_el_veuria_ningu() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        exam.setRestringit(true);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(sessionRepository.existsByExamId(exam.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.publish(exam.getId(), professor))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cap de triat");
        assertThat(exam.getStatus()).isEqualTo(ExamStatus.DRAFT);
    }

    @Test
    void schedule_examen_amb_destinataris_concrets_es_rebutja() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        exam.setRestringit(true);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.schedule(exam.getId(),
                new com.examplatform.dto.ScheduleRequest(LocalDateTime.now().plusDays(1), UUID.randomUUID()), professor))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("destinataris");
    }

    @Test
    void publish_examen_programat_no_es_pot_restringir_a_alumnes() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        exam.setScheduledAt(LocalDateTime.now().plusDays(1));
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.publish(exam.getId(), professor, List.of(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("programat");
        assertThat(exam.getStatus()).isEqualTo(ExamStatus.DRAFT);
    }

    @Test
    void publish_si_l_assignacio_falla_l_error_surt_perque_la_transaccio_es_desfaci() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        List<UUID> alumnes = List.of(UUID.randomUUID());
        when(audiencia.assigna(exam, alumnes)).thenThrow(new IllegalArgumentException("No estan matriculats"));

        assertThatThrownBy(() -> service.publish(exam.getId(), professor, alumnes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publish_noEsDraft_llanca_excepcio() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.publish(exam.getId(), professor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void publish_senseOwnership_llanca_excepcio() {
        Exam exam = exam(ExamStatus.DRAFT, altreProf);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.publish(exam.getId(), professor))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── unpublish ─────────────────────────────────────────────────────────────

    @Test
    void unpublish_published_torna_a_draft() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.unpublish(exam.getId(), professor);

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.DRAFT);
    }

    @Test
    void unpublish_noEsPublished_llanca_excepcio() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.unpublish(exam.getId(), professor))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── close ─────────────────────────────────────────────────────────────────

    @Test
    void close_published_canvia_a_closed() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.close(exam.getId(), professor);

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.CLOSED);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_proprietariPotEsborrar() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        service.delete(exam.getId(), professor);

        verify(examRepository).deleteById(exam.getId());
    }

    @Test
    void delete_esborra_tambe_els_fitxers_pujats_de_les_sessions_de_l_examen() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        ExamSession s1 = ExamSession.builder().id(UUID.randomUUID()).exam(exam).build();
        ExamSession s2 = ExamSession.builder().id(UUID.randomUUID()).exam(exam).build();
        when(sessionRepository.findByExamId(exam.getId())).thenReturn(List.of(s1, s2));

        service.delete(exam.getId(), professor);

        verify(fitxersStorage).esborraSessio(s1.getId());
        verify(fitxersStorage).esborraSessio(s2.getId());
        verify(examRepository).deleteById(exam.getId());
    }

    @Test
    void delete_rebutjat_no_toca_cap_fitxer() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.delete(exam.getId(), professor)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(fitxersStorage);
    }

    @Test
    void delete_senseOwnership_llanca_excepcio() {
        Exam exam = exam(ExamStatus.DRAFT, altreProf);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.delete(exam.getId(), professor))
                .isInstanceOf(AccessDeniedException.class);
        verify(examRepository, never()).deleteById(any());
    }

    @Test
    void delete_examPublicat_llanca_excepcio() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.delete(exam.getId(), professor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
        verify(examRepository, never()).deleteById(any());
    }

    @Test
    void delete_examClosed_llanca_excepcio() {
        Exam exam = exam(ExamStatus.CLOSED, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.delete(exam.getId(), professor))
                .isInstanceOf(IllegalStateException.class);
        verify(examRepository, never()).deleteById(any());
    }

    // ── assertOwnership ───────────────────────────────────────────────────────

    @Test
    void assertOwnership_admin_bypassa_sempre() {
        Exam exam = exam(ExamStatus.DRAFT, altreProf);
        assertThatNoException().isThrownBy(() -> service.assertOwnership(exam, admin));
    }

    @Test
    void assertOwnership_propietari_passa() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        assertThatNoException().isThrownBy(() -> service.assertOwnership(exam, professor));
    }

    @Test
    void assertOwnership_noEsPropietari_llanca_excepcio() {
        Exam exam = exam(ExamStatus.DRAFT, altreProf);
        assertThatThrownBy(() -> service.assertOwnership(exam, professor))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertOwnership_professor_que_imparteix_el_modul_passa() {
        Modul modul = Modul.builder().id(UUID.randomUUID()).codi("0483").nom("SI").build();
        Exam exam = exam(ExamStatus.DRAFT, altreProf);
        exam.setModul(modul);
        when(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId()))
                .thenReturn(true);

        assertThatNoException().isThrownBy(() -> service.assertOwnership(exam, professor));
    }

    @Test
    void assertOwnership_professor_que_no_imparteix_el_modul_llanca_excepcio() {
        Modul modul = Modul.builder().id(UUID.randomUUID()).codi("0484").nom("BD").build();
        Exam exam = exam(ExamStatus.DRAFT, altreProf);
        exam.setModul(modul);
        when(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.assertOwnership(exam, professor))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertOwnership_examen_sense_modul_nomes_permet_al_creador() {
        Exam exam = exam(ExamStatus.DRAFT, altreProf);
        // exam.getModul() == null → imparticioRepository no es crida
        assertThatThrownBy(() -> service.assertOwnership(exam, professor))
                .isInstanceOf(AccessDeniedException.class);
        verify(imparticioRepository, never()).professorImparteixModul(any(), any());
    }

    // ── validateNoConflict ────────────────────────────────────────────────────

    @Test
    void validateNoConflict_solapament_exacte_llanca_excepcio() {
        // Examen existent: 10:00–11:00; nou: 10:30–11:30 → solapament
        UUID grupId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        LocalDateTime existingStart = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);

        Exam existing = exam(ExamStatus.DRAFT, professor);
        existing.setScheduledAt(existingStart);
        existing.setDurada(60);

        when(examRepository.findScheduledForGrup(grupId, examId)).thenReturn(List.of(existing));

        LocalDateTime nouStart = existingStart.plusMinutes(30);
        assertThatThrownBy(() -> service.validateNoConflict(examId, grupId, nouStart, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conflicte");
    }

    @Test
    void validateNoConflict_un_examen_publicat_sense_programar_no_bloqueja() {
        // Abans, qualsevol examen publicat amb alumnes del grup (de qualsevol mòdul) ho impedia
        UUID grupId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        when(examRepository.findScheduledForGrup(grupId, examId)).thenReturn(List.of());

        assertThatNoException().isThrownBy(() ->
                service.validateNoConflict(examId, grupId, LocalDateTime.now().plusHours(1), 60));
    }

    @Test
    void validateNoConflict_sense_solapament_passa() {
        // Existent: 10:00–11:00; nou: 11:00–12:00 → contigus, sense solapament
        UUID grupId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        LocalDateTime existingStart = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);

        Exam existing = exam(ExamStatus.DRAFT, professor);
        existing.setScheduledAt(existingStart);
        existing.setDurada(60);

        when(examRepository.findScheduledForGrup(grupId, examId)).thenReturn(List.of(existing));

        LocalDateTime nouStart = existingStart.plusMinutes(60);  // comença just quan acaba
        assertThatNoException().isThrownBy(
                () -> service.validateNoConflict(examId, grupId, nouStart, 60));
    }

    @Test
    void patchQuestion_resposta_correcta_que_no_es_cap_opcio_es_rebutja() {
        Exam exam = exam(ExamStatus.CLOSED, professor);
        Question q = question(exam);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(questionRepository.findById(q.getId())).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> service.patchQuestion(exam.getId(), q.getId(),
                new QuestionPatchRequest("d", null, null, null), professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a, b, c");
        assertThat(q.getCorrectChoice()).isEqualTo("a");
    }

    // ── reopenWindow ──────────────────────────────────────────────────────────

    @Test
    void reopenWindow_examen_programat_obre_una_finestra_nova_des_d_ara() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        exam.setScheduledAt(LocalDateTime.now().minusHours(1));
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reopenWindow(exam.getId(), professor);

        assertThat(exam.getScheduledAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void reopenWindow_examen_sense_programar_es_rebutja() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.reopenWindow(exam.getId(), professor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no està programat");
        assertThat(exam.getScheduledAt()).isNull();
    }

    // ── updateSettings ────────────────────────────────────────────────────────

    @Test
    void updateSettings_factor_valid_actualitza() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateSettings(exam.getId(), new ExamSettingsRequest(new BigDecimal("0.33")), professor);

        assertThat(exam.getPenalitzacioChoice()).isEqualByComparingTo(new BigDecimal("0.33"));
    }

    @Test
    void updateSettings_activa_i_desactiva_una_pregunta_per_pantalla() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamDto dto = service.updateSettings(exam.getId(), new ExamSettingsRequest(null, true), professor);
        assertThat(exam.isUnaPreguntaPerPantalla()).isTrue();
        assertThat(dto.unaPreguntaPerPantalla()).isTrue();

        service.updateSettings(exam.getId(), new ExamSettingsRequest(null, false), professor);
        assertThat(exam.isUnaPreguntaPerPantalla()).isFalse();
    }

    @Test
    void updateSettings_camps_null_no_es_modifiquen() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        exam.setUnaPreguntaPerPantalla(true);
        exam.setPenalitzacioChoice(new BigDecimal("0.25"));
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateSettings(exam.getId(), new ExamSettingsRequest(null, null), professor);

        assertThat(exam.isUnaPreguntaPerPantalla()).isTrue();
        assertThat(exam.getPenalitzacioChoice()).isEqualByComparingTo("0.25");
    }

    @Test
    void updateSettings_d_un_altre_professor_llanca_AccessDenied() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.updateSettings(exam.getId(), new ExamSettingsRequest(null, true), altreProf))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(exam.isUnaPreguntaPerPantalla()).isFalse();
    }

    @Test
    void updateSettings_factor_negatiu_llanca_excepcio() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() ->
                service.updateSettings(exam.getId(),
                        new ExamSettingsRequest(new BigDecimal("-0.1")), professor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSettings_factor_superiorA1_llanca_excepcio() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() ->
                service.updateSettings(exam.getId(),
                        new ExamSettingsRequest(new BigDecimal("1.01")), professor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSettings_canvia_el_titol_sense_espais_als_extrems() {
        Exam exam = exam(ExamStatus.CLOSED, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateSettings(exam.getId(), new ExamSettingsRequest(null, null, "  Recuperació  ", null), professor);

        assertThat(exam.getTitle()).isEqualTo("Recuperació");
    }

    @Test
    void updateSettings_titol_buit_o_massa_llarg_es_rebutja() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        String abans = exam.getTitle();
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.updateSettings(exam.getId(),
                new ExamSettingsRequest(null, null, "   ", null), professor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateSettings(exam.getId(),
                new ExamSettingsRequest(null, null, "x".repeat(256), null), professor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(exam.getTitle()).isEqualTo(abans);
    }

    @Test
    void updateSettings_canvia_la_durada_d_un_esborrany() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateSettings(exam.getId(), new ExamSettingsRequest(null, null, null, 45), professor);

        assertThat(exam.getDurada()).isEqualTo(45);
    }

    @Test
    void updateSettings_durada_fora_de_limits_es_rebutja() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        int abans = exam.getDurada();
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        for (int d : new int[]{0, -5, ExamService.DURADA_MAXIMA + 1}) {
            assertThatThrownBy(() -> service.updateSettings(exam.getId(),
                    new ExamSettingsRequest(null, null, null, d), professor))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(exam.getDurada()).isEqualTo(abans);
    }

    @Test
    void updateSettings_no_canvia_la_durada_d_un_examen_actiu() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        int abans = exam.getDurada();
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.updateSettings(exam.getId(),
                new ExamSettingsRequest(null, null, null, abans + 30), professor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("esborrany");
        assertThat(exam.getDurada()).isEqualTo(abans);
    }

    @Test
    void updateSettings_mateixa_durada_en_examen_actiu_no_falla() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateSettings(exam.getId(), new ExamSettingsRequest(null, null, "Nou", exam.getDurada()), professor);

        assertThat(exam.getTitle()).isEqualTo("Nou");
    }

    @Test
    void updateSettings_durada_que_solapa_un_altre_examen_programat_del_grup_es_rebutja() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        Grup grup = Grup.builder().id(UUID.randomUUID()).name("1r").build();
        LocalDateTime inici = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        exam.setScheduledAt(inici);
        exam.setScheduledGrup(grup);
        exam.setDurada(60);
        Exam seguent = exam(ExamStatus.DRAFT, professor);
        seguent.setTitle("Següent");
        seguent.setScheduledAt(inici.plusMinutes(90));
        seguent.setDurada(60);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.findScheduledForGrup(grup.getId(), exam.getId())).thenReturn(List.of(seguent));

        assertThatThrownBy(() -> service.updateSettings(exam.getId(),
                new ExamSettingsRequest(null, null, null, 120), professor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Següent");
        assertThat(exam.getDurada()).isEqualTo(60);
    }

    // ── patchQuestion ─────────────────────────────────────────────────────────

    @Test
    void patchQuestion_fixa_i_torna_a_barrejar_les_opcions() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        Question q = question(exam);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(questionRepository.findById(q.getId())).thenReturn(Optional.of(q));
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(q.isBarrejarOpcions()).isTrue();

        service.patchQuestion(exam.getId(), q.getId(),
                new QuestionPatchRequest(null, null, null, null, false), professor);
        assertThat(q.isBarrejarOpcions()).isFalse();

        service.patchQuestion(exam.getId(), q.getId(),
                new QuestionPatchRequest(null, null, null, null, true), professor);
        assertThat(q.isBarrejarOpcions()).isTrue();
    }

    @Test
    void patchQuestion_barrejar_opcions_en_pregunta_no_de_test_llanca_excepcio() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        Question q = question(exam);
        q.setTipus(QuestionType.SHORT);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(questionRepository.findById(q.getId())).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> service.patchQuestion(exam.getId(), q.getId(),
                new QuestionPatchRequest(null, null, null, null, false), professor))
                .isInstanceOf(IllegalArgumentException.class);
        verify(questionRepository, never()).save(any());
    }

    @Test
    void patchQuestion_sense_barrejarOpcions_no_el_modifica() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        Question q = question(exam);
        q.setBarrejarOpcions(false);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(questionRepository.findById(q.getId())).thenReturn(Optional.of(q));
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.patchQuestion(exam.getId(), q.getId(), new QuestionPatchRequest("c", null, null, null), professor);

        assertThat(q.isBarrejarOpcions()).isFalse();
    }

    @Test
    void findById_alumne_rep_si_les_opcions_es_barregen() {
        Exam exam = examAmSolucions();
        exam.getQuestions().get(0).setBarrejarOpcions(false);

        ExamDto dto = service.findById(exam.getId(), user(Role.STUDENT));

        assertThat(dto.questions()).singleElement()
                .satisfies(q -> assertThat(q.barrejarOpcions()).isFalse());
    }

    @Test
    void patchQuestion_actualitza_correctChoice() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        Question q = question(exam);
        q.setCorrectChoice("a");

        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(questionRepository.findById(q.getId())).thenReturn(Optional.of(q));
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.patchQuestion(exam.getId(), q.getId(),
                new QuestionPatchRequest("B", null, null, null), professor);

        assertThat(q.getCorrectChoice()).isEqualTo("b");
    }

    @Test
    void patchQuestion_anulaQuestion() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        Question q = question(exam);

        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(questionRepository.findById(q.getId())).thenReturn(Optional.of(q));
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.patchQuestion(exam.getId(), q.getId(),
                new QuestionPatchRequest(null, true, null, null), professor);

        assertThat(q.isAnulada()).isTrue();
    }

    @Test
    void patchQuestion_descriu_els_canvis_amb_el_valor_anterior() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        Question q = question(exam);
        q.setCorrectChoice("a");
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(questionRepository.findById(q.getId())).thenReturn(Optional.of(q));
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var mod = service.patchQuestion(exam.getId(), q.getId(),
                new QuestionPatchRequest("C", true, null, null), professor);

        assertThat(mod.canvis()).containsExactly("resposta correcta: a → c", "bonus: no → sí");
    }

    @Test
    void patchQuestion_marca_i_desmarca_amb_apunts() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        Question q = question(exam);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(questionRepository.findById(q.getId())).thenReturn(Optional.of(q));
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var mod = service.patchQuestion(exam.getId(), q.getId(),
                new QuestionPatchRequest(null, null, null, null, null, true), professor);

        assertThat(q.isAmbApunts()).isTrue();
        assertThat(mod.canvis()).containsExactly("amb apunts: no → sí");
    }

    @Test
    void patchQuestion_sense_canvis_reals_no_descriu_res() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        Question q = question(exam);
        q.setCorrectChoice("b");
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(questionRepository.findById(q.getId())).thenReturn(Optional.of(q));
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var mod = service.patchQuestion(exam.getId(), q.getId(),
                new QuestionPatchRequest("b", false, null, null), professor);

        assertThat(mod.canvis()).isEmpty();
    }

    @Test
    void patchQuestion_correctChoiceBuit_llanca_excepcio() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        Question q = question(exam);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(questionRepository.findById(q.getId())).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> service.patchQuestion(exam.getId(), q.getId(),
                new QuestionPatchRequest("   ", null, null, null), professor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("buida");
    }

    @Test
    void patchQuestion_senseOwnership_llanca_excepcio() {
        Exam exam = exam(ExamStatus.PUBLISHED, altreProf);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.patchQuestion(exam.getId(), UUID.randomUUID(),
                new QuestionPatchRequest("a", null, null, null), professor))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── assignAula / removeAula ───────────────────────────────────────────────

    @Test
    void assignAula_assigna_correctament() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        Aula aula = aula("A101", "10.0.1.0/24");
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(aulaRepository.findById(aula.getId())).thenReturn(Optional.of(aula));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamDto result = service.assignAula(exam.getId(), aula.getId(), professor);

        assertThat(exam.getAula()).isEqualTo(aula);
        assertThat(result.aulaId()).isEqualTo(aula.getId());
    }

    @Test
    void assignAula_aulaInexistent_llanca_excepcio() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        UUID aulaId = UUID.randomUUID();
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(aulaRepository.findById(aulaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignAula(exam.getId(), aulaId, professor))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void assignAula_senseOwnership_llanca_excepcio() {
        Exam exam = exam(ExamStatus.DRAFT, altreProf);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.assignAula(exam.getId(), UUID.randomUUID(), professor))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void removeAula_treu_aula_de_lexamen() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        exam.setAula(aula("A101", "10.0.1.0/24"));
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamDto result = service.removeAula(exam.getId(), professor);

        assertThat(exam.getAula()).isNull();
        assertThat(result.aulaId()).isNull();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // ── findById: qui veu les solucions ─────────────────────────────────────

    private Exam examAmSolucions() {
        Exam exam = exam(ExamStatus.PUBLISHED, professor);
        exam.setQuestions(new ArrayList<>());
        Question q = question(exam);
        q.setCorrectChoice("b");
        q.setModelResposta("model");
        q.setClaus("DHCP");
        q.setRa("RA1");
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        return exam;
    }

    private void assertAmbSolucions(ExamDto dto) {
        assertThat(dto.questions()).singleElement().satisfies(q -> {
            assertThat(q.correctChoice()).isEqualTo("b");
            assertThat(q.modelResposta()).isEqualTo("model");
            assertThat(q.claus()).isEqualTo("DHCP");
        });
    }

    private void assertSenseSolucions(ExamDto dto) {
        assertThat(dto.questions()).singleElement().satisfies(q -> {
            assertThat(q.enunciat()).isEqualTo("Pregunta test");
            assertThat(q.correctChoice()).isNull();
            assertThat(q.modelResposta()).isNull();
            assertThat(q.claus()).isNull();
            assertThat(q.outputContains()).isNull();
            assertThat(q.testScript()).isNull();
            assertThat(q.ra()).isNull();
        });
    }

    @Test
    void findById_creador_veu_les_solucions() {
        Exam exam = examAmSolucions();
        assertAmbSolucions(service.findById(exam.getId(), professor));
    }

    @Test
    void findById_admin_veu_les_solucions() {
        Exam exam = examAmSolucions();
        assertAmbSolucions(service.findById(exam.getId(), admin));
    }

    @Test
    void findById_professor_del_modul_veu_les_solucions() {
        Exam exam = examAmSolucions();
        Modul modul = Modul.builder().id(UUID.randomUUID()).codi("0483").nom("SI")
                .cicle(Cicle.builder().id(UUID.randomUUID()).nom("ASIX").build()).build();
        exam.setModul(modul);
        when(imparticioRepository.professorImparteixModul(altreProf.getId(), modul.getId())).thenReturn(true);

        assertAmbSolucions(service.findById(exam.getId(), altreProf));
    }

    @Test
    void findById_altre_professor_no_veu_les_solucions() {
        Exam exam = examAmSolucions();
        assertSenseSolucions(service.findById(exam.getId(), altreProf));
    }

    @Test
    void findById_alumne_no_veu_les_solucions() {
        Exam exam = examAmSolucions();
        assertSenseSolucions(service.findById(exam.getId(), user(Role.STUDENT)));
        verifyNoInteractions(imparticioRepository);
    }

    @Test
    void schedule_al_grup_d_un_altre_professor_llanca_AccessDenied() {
        Exam exam = exam(ExamStatus.DRAFT, professor);
        Grup grupAlie = Grup.builder().id(UUID.randomUUID()).name("Aliè").createdBy(altreProf).build();
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(grupRepository.findById(grupAlie.getId())).thenReturn(Optional.of(grupAlie));

        assertThatThrownBy(() -> service.schedule(exam.getId(),
                new ScheduleRequest(LocalDateTime.now().plusDays(1), grupAlie.getId()), professor))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(exam.getScheduledGrup()).isNull();
    }

    // ── publicarNotes ─────────────────────────────────────────────────────────

    @Test
    void publicarNotes_sense_pendents_les_fa_visibles() {
        Exam exam = exam(ExamStatus.CLOSED, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(answerRepository.findPendentsRevisio(exam.getId(), CorrectionService.PENDENTS_TIPUS_EXCLOSOS))
                .thenReturn(List.of());
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.publicarNotes(exam.getId(), professor);

        assertThat(exam.isNotesVisibles()).isTrue();
    }

    @Test
    void publicarNotes_amb_propostes_pendents_llanca_excepcio_i_no_publica() {
        Exam exam = exam(ExamStatus.CLOSED, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(answerRepository.findPendentsRevisio(exam.getId(), CorrectionService.PENDENTS_TIPUS_EXCLOSOS))
                .thenReturn(List.of(new Answer(), new Answer()));

        assertThatThrownBy(() -> service.publicarNotes(exam.getId(), professor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 respostes pendents");
        assertThat(exam.isNotesVisibles()).isFalse();
        verify(examRepository, never()).save(any());
    }

    @Test
    void publicarNotes_d_un_altre_professor_llanca_AccessDenied() {
        Exam exam = exam(ExamStatus.CLOSED, professor);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.publicarNotes(exam.getId(), altreProf))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(answerRepository);
    }

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).role(role)
                .email(UUID.randomUUID() + "@test.cat").name("Test").build();
    }

    private Exam exam(ExamStatus status, User owner) {
        Exam e = Exam.builder().id(UUID.randomUUID()).title("Examen test")
                .durada(60).status(status).createdBy(owner)
                .penalitzacioChoice(BigDecimal.ZERO).build();
        e.getQuestions().add(pregunta(e, 1, QuestionType.SHORT, "10"));
        return e;
    }

    private static Question pregunta(Exam e, int ordre, QuestionType tipus, String punts) {
        return Question.builder().id(UUID.randomUUID()).exam(e).ordre(ordre).tipus(tipus)
                .enunciat("P" + ordre).punts(new BigDecimal(punts)).build();
    }

    private Aula aula(String nom, String cidr) {
        return Aula.builder().id(UUID.randomUUID()).nom(nom).xarxaCidr(cidr).build();
    }

    private Question question(Exam exam) {
        Question q = Question.builder().id(UUID.randomUUID()).exam(exam)
                .tipus(QuestionType.CHOICE).punts(new BigDecimal("2"))
                .choices("a) u\nb) dos\nc) tres").correctChoice("a")
                .enunciat("Pregunta test").ordre(1).build();
        exam.getQuestions().add(q);
        return q;
    }
}

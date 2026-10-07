package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.QuestionEditRequest;
import com.examplatform.infrastructure.parser.MarkdownExamParser;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import com.examplatform.infrastructure.persistence.QuestionFileRepository;
import com.examplatform.infrastructure.persistence.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Regles de l'editor: mateixes validacions que la importació, i bloqueig quan l'examen té sessions. */
class EditorPreguntesServiceTest {

    ExamService examService = mock(ExamService.class);
    QuestionRepository questionRepository = mock(QuestionRepository.class);
    QuestionFileRepository fileRepository = mock(QuestionFileRepository.class);
    ExamSessionRepository sessionRepository = mock(ExamSessionRepository.class);
    EditorPreguntesService service;

    User prof = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
    Exam exam;
    Question q1;

    @BeforeEach
    void setUp() {
        service = new EditorPreguntesService(examService, new MarkdownExamParser(), questionRepository,
                fileRepository, sessionRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "imatgeMaxBytes", 5L * 1024 * 1024);
        exam = Exam.builder().id(UUID.randomUUID()).title("Ex").questions(new ArrayList<>()).build();
        q1 = Question.builder().id(UUID.randomUUID()).exam(exam).ordre(1).tipus(QuestionType.SHORT)
                .enunciat("vella").punts(new BigDecimal("10")).build();
        exam.getQuestions().add(q1);
        when(examService.getEntity(exam.getId())).thenReturn(exam);
        when(questionRepository.save(any(Question.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static QuestionEditRequest curta(String enunciat, String punts) {
        return new QuestionEditRequest(QuestionType.SHORT, enunciat, new BigDecimal(punts), null, null,
                "model", null, null, null, null, null, "RA1", "alta", null, null, null, null);
    }

    private static QuestionEditRequest test(String correcta, String... opcions) {
        return new QuestionEditRequest(QuestionType.CHOICE, "Quina?", new BigDecimal("2"), List.of(opcions),
                correcta, null, null, null, null, null, null, null, null, false, null, null, null);
    }

    @Test
    void actualitza_reescriu_el_contingut_i_conserva_id_i_ordre() {
        Question r = service.actualitza(exam.getId(), q1.getId(), curta("nova", "4.5"), prof);

        assertThat(r.getId()).isEqualTo(q1.getId());
        assertThat(r.getOrdre()).isEqualTo(1);
        assertThat(r.getEnunciat()).isEqualTo("nova");
        assertThat(r.getPunts()).isEqualByComparingTo("4.5");
        assertThat(r.getModelResposta()).isEqualTo("model");
        assertThat(r.getRa()).isEqualTo("RA1");
        assertThat(r.getDificultat()).isEqualTo("alta");
    }

    @Test
    void actualitza_pregunta_de_test_amb_opcions_resposta_i_ordre_fix() {
        Question r = service.actualitza(exam.getId(), q1.getId(), test("b", "uno", "dos", "tres"), prof);

        assertThat(r.getTipus()).isEqualTo(QuestionType.CHOICE);
        assertThat(r.getChoices()).isEqualTo("a) uno\nb) dos\nc) tres");
        assertThat(r.getCorrectChoice()).isEqualTo("b");
        assertThat(r.isBarrejarOpcions()).isFalse();
    }

    @Test
    void actualitza_rebutja_resposta_correcta_que_no_es_una_opcio_sense_mostrar_linies() {
        assertThatThrownBy(() -> service.actualitza(exam.getId(), q1.getId(), test("d", "uno", "dos"), prof))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no és cap de les opcions")
                .hasMessageNotContaining("Línia")
                .hasMessageNotContaining("línia");
        assertThat(q1.getEnunciat()).isEqualTo("vella");
    }

    @Test
    void actualitza_rebutja_test_sense_opcions_i_enunciat_buit_i_punts_invalids() {
        assertThatThrownBy(() -> service.actualitza(exam.getId(), q1.getId(), test("a"), prof))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sense opcions");
        assertThatThrownBy(() -> service.actualitza(exam.getId(), q1.getId(), curta("  ", "2"), prof))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("enunciat");
        for (String punts : new String[]{"0", "-1", "11", "1.234"}) {
            assertThatThrownBy(() -> service.actualitza(exam.getId(), q1.getId(), curta("x", punts), prof))
                    .as(punts).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("punts");
        }
    }

    @Test
    void actualitza_aplica_les_regles_d_import_per_tipus_p_ex_output_en_una_pregunta_de_text() {
        var req = new QuestionEditRequest(QuestionType.SHORT, "x", new BigDecimal("2"), null, null, null,
                "hola", null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.actualitza(exam.getId(), q1.getId(), req, prof))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("output-contains");
    }

    @Test
    void actualitza_rebutja_enunciat_que_s_interpretaria_com_una_altra_pregunta() {
        assertThatThrownBy(() -> service.actualitza(exam.getId(), q1.getId(), curta("a\n## 2. [short] [pts:1]\nb", "2"), prof))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualitza_etiquetes_amb_claudators_es_rebutgen() {
        var req = new QuestionEditRequest(QuestionType.SHORT, "x", new BigDecimal("2"), null, null, null,
                null, null, null, null, null, "RA1] [dif:alta", null, null, null, null, null);
        assertThatThrownBy(() -> service.actualitza(exam.getId(), q1.getId(), req, prof))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ra");
    }

    @Test
    void actualitza_una_seccio_nomes_necessita_titol() {
        var req = new QuestionEditRequest(QuestionType.SECTION, "Part 2", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        Question r = service.actualitza(exam.getId(), q1.getId(), req, prof);
        assertThat(r.getTipus()).isEqualTo(QuestionType.SECTION);
        assertThat(r.getEnunciat()).isEqualTo("Part 2");
        assertThat(r.getPunts()).isEqualByComparingTo("0");
    }

    @Test
    void amb_sessions_d_alumnes_no_es_pot_editar_ni_afegir_ni_eliminar_ni_moure() {
        when(sessionRepository.existsByExamId(exam.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.actualitza(exam.getId(), q1.getId(), curta("x", "2"), prof))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sessions");
        assertThatThrownBy(() -> service.afegeix(exam.getId(), curta("x", "2"), prof))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.elimina(exam.getId(), q1.getId(), prof))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.mou(exam.getId(), q1.getId(), 1, prof))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.estat(exam.getId(), prof).editable()).isFalse();
        verify(questionRepository, never()).save(any());
    }

    @Test
    void sense_sessions_l_examen_es_editable() {
        assertThat(service.estat(exam.getId(), prof).editable()).isTrue();
    }

    @Test
    void qui_no_gestiona_l_examen_no_pot_editar() {
        doThrow(new org.springframework.security.access.AccessDeniedException("no"))
                .when(examService).assertOwnership(exam, prof);
        assertThatThrownBy(() -> service.actualitza(exam.getId(), q1.getId(), curta("x", "2"), prof))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void pregunta_d_un_altre_examen_no_es_troba() {
        assertThatThrownBy(() -> service.actualitza(exam.getId(), UUID.randomUUID(), curta("x", "2"), prof))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    // ── Imatges ───────────────────────────────────────────────────────────────

    @Test
    void la_signatura_decideix_si_es_una_imatge() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();

        assertThat(EditorPreguntesService.ambFirmaDImatge(png, "png")).isTrue();
        assertThat(EditorPreguntesService.ambFirmaDImatge(jpg, "jpg")).isTrue();
        assertThat(EditorPreguntesService.ambFirmaDImatge(gif, "gif")).isTrue();
        assertThat(EditorPreguntesService.ambFirmaDImatge(webp, "webp")).isTrue();
        assertThat(EditorPreguntesService.ambFirmaDImatge(html, "png")).isFalse();
        assertThat(EditorPreguntesService.ambFirmaDImatge(png, "jpg")).isFalse();
        assertThat(EditorPreguntesService.ambFirmaDImatge(new byte[3], "png")).isFalse();
    }

    @Test
    void pujaImatge_rebutja_formats_no_admesos_i_fitxers_falsos() {
        when(questionRepository.findById(q1.getId())).thenReturn(java.util.Optional.of(q1));
        var svg = new MockMultipartFile("file", "dibuix.svg", "image/svg+xml", "<svg onload=alert(1)/>".getBytes());
        var fals = new MockMultipartFile("file", "foto.png", "image/png", "no soc una imatge!!".getBytes());

        assertThatThrownBy(() -> service.pujaImatge(q1.getId(), svg, prof))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no admès");
        assertThatThrownBy(() -> service.pujaImatge(q1.getId(), fals, prof))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no és una imatge");
        verify(fileRepository, never()).save(any());
    }
}

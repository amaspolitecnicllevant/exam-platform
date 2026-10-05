package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ExamDto;
import com.examplatform.infrastructure.persistence.ExamRepository;
import com.examplatform.infrastructure.persistence.QuestionFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DuplicacioExamenServiceTest {

    @Mock ExamService examService;
    @Mock ExamRepository examRepository;
    @Mock QuestionFileRepository questionFileRepository;
    @TempDir Path files;

    DuplicacioExamenService service;
    final User creador = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).name("Creador").build();
    final User company = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).name("Company").build();
    Exam origen;
    Question q1, q2;

    @BeforeEach
    void setUp() {
        service = new DuplicacioExamenService(examService, examRepository, questionFileRepository);
        ReflectionTestUtils.setField(service, "filesHostPath", files.toString());

        Modul modul = Modul.builder().id(UUID.randomUUID()).nom("Sistemes")
                .cicle(Cicle.builder().id(UUID.randomUUID()).nom("ASIX").build()).build();
        origen = Exam.builder().id(UUID.randomUUID()).title("Parcial 1").durada(60).instruccions("Sort!")
                .status(ExamStatus.CLOSED).createdBy(creador).rawMd("# Parcial 1").modul(modul)
                .scheduledAt(LocalDateTime.now()).notesVisibles(true).unaPreguntaPerPantalla(true)
                .penalitzacioChoice(new BigDecimal("0.33")).createdAt(LocalDateTime.now()).build();
        q1 = Question.builder().id(UUID.randomUUID()).exam(origen).ordre(1).tipus(QuestionType.CHOICE)
                .enunciat("Quina?").punts(new BigDecimal("1.00")).choices("[\"a\",\"b\"]").correctChoice("b")
                .barrejarOpcions(false).anulada(true).build();
        q2 = Question.builder().id(UUID.randomUUID()).exam(origen).ordre(2).tipus(QuestionType.BASH_SCRIPT)
                .enunciat("Script").punts(new BigDecimal("2.00")).outputContains("ok").testScript("test.sh")
                .ambApunts(true).ra("RA2").build();
        origen.getQuestions().addAll(List.of(q1, q2));

        lenient().when(questionFileRepository.findByQuestionId(any())).thenReturn(List.of());
        lenient().when(examService.getEntity(origen.getId())).thenReturn(origen);
        // save persisteix: assigna ids a l'examen i a les preguntes, com Hibernate
        lenient().when(examRepository.save(any())).thenAnswer(inv -> {
            Exam e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setCreatedAt(LocalDateTime.now());
            e.getQuestions().forEach(q -> q.setId(UUID.randomUUID()));
            return e;
        });
    }

    private Exam copiaDesada() {
        ArgumentCaptor<Exam> c = ArgumentCaptor.forClass(Exam.class);
        verify(examRepository).save(c.capture());
        return c.getValue();
    }

    @Test
    void crea_un_esborrany_nou_amb_les_preguntes_i_la_configuracio() {
        ExamDto dto = service.duplica(origen.getId(), creador);

        Exam copia = copiaDesada();
        assertThat(dto.id()).isEqualTo(copia.getId()).isNotEqualTo(origen.getId());
        assertThat(copia.getTitle()).isEqualTo("Còpia de Parcial 1");
        assertThat(copia.getStatus()).isEqualTo(ExamStatus.DRAFT);
        assertThat(copia.getDurada()).isEqualTo(60);
        assertThat(copia.getInstruccions()).isEqualTo("Sort!");
        assertThat(copia.getRawMd()).isEqualTo("# Parcial 1");
        assertThat(copia.getModul()).isSameAs(origen.getModul());
        assertThat(copia.getPenalitzacioChoice()).isEqualByComparingTo("0.33");
        assertThat(copia.isUnaPreguntaPerPantalla()).isTrue();

        assertThat(copia.getQuestions()).hasSize(2);
        Question c1 = copia.getQuestions().get(0), c2 = copia.getQuestions().get(1);
        assertThat(c1.getExam()).isSameAs(copia);
        assertThat(c1.getId()).isNotEqualTo(q1.getId());
        assertThat(c1.getOrdre()).isEqualTo(1);
        assertThat(c1.getChoices()).isEqualTo(q1.getChoices());
        assertThat(c1.getCorrectChoice()).isEqualTo("b");
        assertThat(c1.isBarrejarOpcions()).isFalse();
        assertThat(c2.getOutputContains()).isEqualTo("ok");
        assertThat(c2.getTestScript()).isEqualTo("test.sh");
        assertThat(c2.isAmbApunts()).isTrue();
        assertThat(c2.getRa()).isEqualTo("RA2");
    }

    @Test
    void no_copia_programacio_ni_estat_de_correccio() {
        service.duplica(origen.getId(), creador);

        Exam copia = copiaDesada();
        assertThat(copia.getScheduledAt()).isNull();
        assertThat(copia.getScheduledGrup()).isNull();
        assertThat(copia.isNotesVisibles()).isFalse();
        assertThat(copia.getQuestions().get(0).isAnulada()).isFalse();
    }

    @Test
    void la_copia_es_de_qui_la_fa() {
        service.duplica(origen.getId(), company);

        assertThat(copiaDesada().getCreatedBy()).isSameAs(company);
    }

    @Test
    void sense_permis_no_duplica() {
        doThrow(new AccessDeniedException("no")).when(examService).assertOwnership(origen, company);

        assertThatThrownBy(() -> service.duplica(origen.getId(), company)).isInstanceOf(AccessDeniedException.class);
        verify(examRepository, never()).save(any());
    }

    @Test
    void copia_els_fitxers_de_dades_a_la_carpeta_de_la_pregunta_nova() throws Exception {
        Path dirOrigen = Files.createDirectories(files.resolve("questions").resolve(q2.getId().toString()));
        Path font = Files.writeString(dirOrigen.resolve("dades.txt"), "1 2 3");
        when(questionFileRepository.findByQuestionId(q2.getId())).thenReturn(List.of(QuestionFile.builder()
                .id(UUID.randomUUID()).question(q2).filename("dades.txt").storedPath(font.toString())
                .contentType("text/plain").fileSize(5).build()));

        service.duplica(origen.getId(), creador);

        Question c2 = copiaDesada().getQuestions().get(1);
        Path esperat = files.resolve("questions").resolve(c2.getId().toString()).resolve("dades.txt");
        assertThat(esperat).hasContent("1 2 3");
        assertThat(font).exists();   // l'original no es toca

        ArgumentCaptor<QuestionFile> qf = ArgumentCaptor.forClass(QuestionFile.class);
        verify(questionFileRepository).save(qf.capture());
        assertThat(qf.getValue().getQuestion()).isSameAs(c2);
        assertThat(qf.getValue().getStoredPath()).isEqualTo(esperat.toString());
        assertThat(qf.getValue().getFilename()).isEqualTo("dades.txt");
        assertThat(qf.getValue().getFileSize()).isEqualTo(5);
    }

    @Test
    void si_falta_un_fitxer_falla_i_no_deixa_fitxers_orfes() throws Exception {
        Path dirOrigen = Files.createDirectories(files.resolve("questions").resolve(q1.getId().toString()));
        Path bo = Files.writeString(dirOrigen.resolve("a.txt"), "a");
        when(questionFileRepository.findByQuestionId(q1.getId())).thenReturn(List.of(
                QuestionFile.builder().question(q1).filename("a.txt").storedPath(bo.toString()).build(),
                QuestionFile.builder().question(q1).filename("perdut.txt")
                        .storedPath(dirOrigen.resolve("perdut.txt").toString()).build()));

        assertThatThrownBy(() -> service.duplica(origen.getId(), creador))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("perdut.txt");

        Question c1 = copiaDesada().getQuestions().get(0);
        assertThat(files.resolve("questions").resolve(c1.getId().toString()).resolve("a.txt")).doesNotExist();
    }

    @Test
    void titol_llarg_es_retalla_a_la_mida_de_la_columna() {
        assertThat(DuplicacioExamenService.titolCopia("x".repeat(255))).hasSize(255).startsWith("Còpia de ");
    }
}

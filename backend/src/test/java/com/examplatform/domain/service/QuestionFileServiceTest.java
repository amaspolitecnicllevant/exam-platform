package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import com.examplatform.infrastructure.persistence.QuestionFileRepository;
import com.examplatform.infrastructure.persistence.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionFileServiceTest {

    @Mock QuestionFileRepository fileRepository;
    @Mock QuestionRepository questionRepository;
    @Mock ExamService examService;
    @Mock ExamSessionRepository sessionRepository;
    @TempDir Path files;

    QuestionFileService service;
    Exam exam;
    Question question;
    final User professor = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
    final User alumne = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();

    @BeforeEach
    void setUp() {
        service = new QuestionFileService(fileRepository, questionRepository, examService, sessionRepository);
        ReflectionTestUtils.setField(service, "filesHostPath", files.toString());
        exam = Exam.builder().id(UUID.randomUUID()).build();
        question = Question.builder().id(UUID.randomUUID()).exam(exam).build();
    }

    private void denegaGestio() {
        doThrow(new AccessDeniedException("no")).when(examService).assertOwnership(exam, professor);
    }

    private QuestionFile fitxerDesat() throws Exception {
        Path p = files.resolve("dades.csv");
        Files.writeString(p, "a,b");
        QuestionFile qf = QuestionFile.builder().id(UUID.randomUUID()).question(question)
                .filename("dades.csv").storedPath(p.toString()).contentType("text/csv").build();
        when(fileRepository.findById(qf.getId())).thenReturn(Optional.of(qf));
        return qf;
    }

    @Test
    void professor_sense_permis_no_pot_pujar() {
        when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
        denegaGestio();

        assertThatThrownBy(() -> service.upload(question.getId(),
                new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes()), professor))
                .isInstanceOf(AccessDeniedException.class);
        verify(fileRepository, never()).save(any());
        assertThat(files.resolve("questions")).doesNotExist();
    }

    @Test
    void professor_amb_permis_puja() throws Exception {
        when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
        when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.upload(question.getId(), new MockMultipartFile("file", "dades.csv", "text/csv", "1,2".getBytes()), professor);

        assertThat(files.resolve("questions").resolve(question.getId().toString()).resolve("dades.csv")).hasContent("1,2");
    }

    @Test
    void professor_sense_permis_no_pot_esborrar() {
        when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
        denegaGestio();

        assertThatThrownBy(() -> service.delete(question.getId(), UUID.randomUUID(), professor))
                .isInstanceOf(AccessDeniedException.class);
        verify(fileRepository, never()).delete(any());
    }

    @Test
    void alumne_amb_sessio_de_l_examen_descarrega() throws Exception {
        QuestionFile qf = fitxerDesat();
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), alumne.getId()))
                .thenReturn(Optional.of(new ExamSession()));

        QuestionFileService.Descarrega d = service.download(qf.getId(), alumne);

        assertThat(d.filename()).isEqualTo("dades.csv");
        assertThat(d.contentType()).isEqualTo("text/csv");
    }

    @Test
    void alumne_sense_sessio_de_l_examen_no_descarrega() throws Exception {
        QuestionFile qf = fitxerDesat();
        when(sessionRepository.findByExamIdAndStudentId(exam.getId(), alumne.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(qf.getId(), alumne)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void professor_d_un_altre_examen_no_descarrega_ni_llista() throws Exception {
        QuestionFile qf = fitxerDesat();
        when(examService.potGestionar(exam, professor)).thenReturn(false);
        when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.download(qf.getId(), professor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.list(question.getId(), professor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void professor_de_l_examen_llista() {
        when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
        when(examService.potGestionar(exam, professor)).thenReturn(true);
        when(fileRepository.findByQuestionId(question.getId())).thenReturn(List.of());

        assertThat(service.list(question.getId(), professor)).isEmpty();
    }
}

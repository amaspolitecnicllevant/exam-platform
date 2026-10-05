package com.examplatform.controller;

import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.User;
import com.examplatform.domain.service.AuditLogService;
import com.examplatform.domain.service.ExamService;
import com.examplatform.domain.service.SessionService;
import com.examplatform.dto.ExamDto;
import com.examplatform.dto.ExamSettingsRequest;
import com.examplatform.dto.QuestionDto;
import com.examplatform.dto.QuestionPatchRequest;
import com.examplatform.dto.ScheduleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final SessionService sessionService;
    private final AuditLogService auditLog;
    private final com.examplatform.domain.service.StatsService statsService;
    private final com.examplatform.domain.service.CopiesService copiesService;
    private final com.examplatform.domain.service.DuplicacioExamenService duplicacioService;
    private final com.examplatform.domain.service.RecuperacioService recuperacioService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ResponseEntity<ExamDto> createFromMd(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User professor) throws IOException {
        String md = new String(file.getBytes(), StandardCharsets.UTF_8);
        ExamDto result = examService.createFromMd(md, professor);
        auditLog.log(professor.getId(), "EXAM_CREATED", result.id().toString());
        return ResponseEntity.ok(result);
    }

    /** Duplica l'examen com a esborrany nou (preguntes, configuració i fitxers de dades). */
    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto duplicate(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        ExamDto copia = duplicacioService.duplica(id, user);
        auditLog.log(user.getId(), "EXAM_DUPLICATED", copia.id() + " (origen " + id + ")");
        return copia;
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public List<ExamDto> myExams(@AuthenticationPrincipal User professor) {
        return examService.findByProfessor(professor.getId());
    }

    @GetMapping("/published")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ExamDto> published(@AuthenticationPrincipal User student) {
        return examService.findPublishedForStudent(student);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ExamDto findById(@PathVariable UUID id,
                            @AuthenticationPrincipal User user) {
        return examService.findById(id, user);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto publish(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        ExamDto result = examService.publish(id, user);
        auditLog.log(user.getId(), "EXAM_PUBLISHED", id.toString());
        return result;
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto unpublish(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return examService.unpublish(id, user);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto close(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        ExamDto result = examService.close(id, user);
        int entregades = sessionService.entregaEnCurs(id);
        auditLog.log(user.getId(), "EXAM_CLOSED", id + (entregades > 0 ? " (" + entregades + " sessions en curs entregades)" : ""));
        return result;
    }

    @PostMapping("/{id}/publicar-notes")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto publicarNotes(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        ExamDto result = examService.publicarNotes(id, user);
        auditLog.log(user.getId(), "NOTES_PUBLICADES", id.toString());
        return result;
    }

    @PostMapping("/{id}/ocultar-notes")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto ocultarNotes(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return examService.ocultarNotes(id, user);
    }

    @PatchMapping("/{id}/modul/{modulId}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto assignModul(@PathVariable UUID id,
                               @PathVariable UUID modulId,
                               @AuthenticationPrincipal User user) {
        return examService.assignModul(id, modulId, user);
    }

    @PatchMapping("/{id}/aula/{aulaId}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto assignAula(@PathVariable UUID id,
                              @PathVariable UUID aulaId,
                              @AuthenticationPrincipal User user) {
        return examService.assignAula(id, aulaId, user);
    }

    @DeleteMapping("/{id}/aula")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto removeAula(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return examService.removeAula(id, user);
    }

    @PatchMapping("/{examId}/questions/{questionId}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public QuestionDto patchQuestion(@PathVariable UUID examId,
                                     @PathVariable UUID questionId,
                                     @RequestBody QuestionPatchRequest req,
                                     @AuthenticationPrincipal User user) {
        ExamService.ModificacioPregunta mod = examService.patchQuestion(examId, questionId, req, user);
        Question q = mod.pregunta();
        // Només cal re-corregir si canvia la resposta correcta o es treu el bonus d'una pregunta de test
        boolean recorregir = mod.canvis().stream()
                .anyMatch(c -> c.startsWith("resposta correcta") || c.startsWith("bonus"));
        int recorregides = recorregir ? sessionService.reCorrectQuestion(q) : 0;
        if (!mod.canvis().isEmpty()) {
            String detall = "pregunta " + q.getOrdre() + " (" + questionId + "): " + String.join("; ", mod.canvis())
                    + (recorregides > 0 ? "; " + recorregides + " respostes re-corregides" : "");
            auditLog.log(user.getId(), mod.canvis().stream().anyMatch(c -> c.startsWith("resposta correcta") || c.startsWith("bonus"))
                    ? "QUESTION_RECORRECTED" : "QUESTION_PATCHED", detall);
        }
        return QuestionDto.from(q);
    }

    @GetMapping("/{id}/copies")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public com.examplatform.dto.CopiesInformeDto copies(@PathVariable UUID id,
                                                        @AuthenticationPrincipal User user) {
        com.examplatform.dto.CopiesInformeDto informe = copiesService.informe(id, user);
        auditLog.log(user.getId(), "COPIES_REPORT_VIEWED", "examen " + id + ": " + informe.parelles().size() + " parells");
        return informe;
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public com.examplatform.dto.ExamStatsDto stats(@PathVariable UUID id,
                                                   @AuthenticationPrincipal User user) {
        return statsService.stats(id, user);
    }

    /** Alumnes suspesos i no presentats, candidats a la recuperació. */
    @GetMapping("/{id}/recuperacio")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public com.examplatform.dto.RecuperacioDto recuperacio(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return recuperacioService.candidats(id, user);
    }

    /** Crea un grup amb els alumnes triats per fer-hi la recuperació. */
    @PostMapping("/{id}/recuperacio/grup")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public com.examplatform.dto.GrupDto crearGrupRecuperacio(@PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody com.examplatform.dto.RecuperacioDto.CrearGrupRequest req,
            @AuthenticationPrincipal User user) {
        com.examplatform.dto.GrupDto grup = recuperacioService.crearGrup(id, req, user);
        auditLog.log(user.getId(), "GRUP_RECUPERACIO", grup.id() + " (examen " + id + ", "
                + grup.students().size() + " alumnes)");
        return grup;
    }

    @PatchMapping("/{id}/settings")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto updateSettings(@PathVariable UUID id,
                                  @RequestBody ExamSettingsRequest req,
                                  @AuthenticationPrincipal User user) {
        ExamDto antic = examService.findById(id, user);
        ExamDto dto = examService.updateSettings(id, req, user);
        if (antic.penalitzacioChoice().compareTo(dto.penalitzacioChoice()) != 0) {
            // Les notes de test ja entregades es calculen amb la penalització: cal refer-les
            int recorregides = sessionService.reCorregeixTest(id);
            auditLog.log(user.getId(), "EXAM_PENALITZACIO", id + ": " + antic.penalitzacioChoice() + " → "
                    + dto.penalitzacioChoice() + " (" + recorregides + " respostes de test re-corregides)");
        }
        if (!antic.title().equals(dto.title())) {
            auditLog.log(user.getId(), "EXAM_RENAMED", id + ": «" + antic.title() + "» → «" + dto.title() + "»");
        }
        if (antic.durada() != dto.durada()) {
            auditLog.log(user.getId(), "EXAM_DURADA", id + ": " + antic.durada() + " → " + dto.durada() + " min");
        }
        return dto;
    }

    @PostMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto schedule(@PathVariable UUID id,
                            @RequestBody ScheduleRequest req,
                            @AuthenticationPrincipal User user) {
        return examService.schedule(id, req, user);
    }

    @DeleteMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto unschedule(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return examService.unschedule(id, user);
    }

    @PostMapping("/{id}/reopen-window")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto reopenWindow(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return examService.reopenWindow(id, user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        examService.delete(id, user);
        auditLog.log(user.getId(), "EXAM_DELETED", id.toString());
        return ResponseEntity.noContent().build();
    }
}

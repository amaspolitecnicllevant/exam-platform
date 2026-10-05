package com.examplatform.controller;

import com.examplatform.domain.service.Puntuacio;
import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.model.*;
import com.examplatform.domain.service.*;
import com.examplatform.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExamSessionRepository sessionRepository;
    private final AnswerRepository answerRepository;
    private final ExamService examService;

    @GetMapping("/exam/{examId}/csv")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ResponseEntity<byte[]> exportCsv(@PathVariable UUID examId,
                                             @AuthenticationPrincipal User professor) throws IOException {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, professor);
        List<ExamSession> sessions = sessionRepository.findByExamIdWithDetails(examId);

        List<Question> preguntes = exam.getQuestions().stream()
                .filter(q -> q.getTipus() != QuestionType.SECTION)
                .sorted(java.util.Comparator.comparingInt(Question::getOrdre))
                .toList();

        StringWriter sw = new StringWriter();
        try (CSVPrinter csv = new CSVPrinter(sw, CSVFormat.EXCEL.builder()
                .setHeader("alumne", "email", "pregunta", "tipus", "punts_max",
                           "resposta", "output_exec", "auto_score", "manual_score", "bonus", "punts", "comentari")
                .build())) {

            for (ExamSession session : sessions) {
                java.util.Map<java.util.UUID, Answer> perPregunta = new java.util.HashMap<>();
                answerRepository.findBySessionId(session.getId())
                        .forEach(a -> perPregunta.put(a.getQuestion().getId(), a));
                // El nom el tria l'alumne en registrar-se: també es neteja de fórmules
                String studentName  = sanitizeCsvCell(session.getStudent().getName());
                String studentEmail = sanitizeCsvCell(session.getStudent().getEmail());

                // Una fila per pregunta, també les no respostes (una pregunta amb bonus compta per a tothom)
                for (Question q : preguntes) {
                    Answer answer = perPregunta.get(q.getId());
                    csv.printRecord(
                            studentName, studentEmail,
                            q.getOrdre(), q.getTipus(), q.getPunts(),
                            answer != null ? sanitizeCsvCell(answer.getContingut()) : "",
                            answer != null ? sanitizeCsvCell(answer.getExecutionOutput()) : "",
                            answer != null ? answer.getAutoScore() : null,
                            answer != null ? answer.getManualScore() : null,
                            q.isAnulada() ? "sí" : "",
                            Puntuacio.punts(q, answer),
                            answer != null ? sanitizeCsvCell(answer.getComentari()) : ""
                    );
                }
                csv.printRecord(studentName, studentEmail, "TOTAL", "", Puntuacio.maxim(preguntes), "", "", "", "", "",
                        Puntuacio.total(preguntes, perPregunta), "");
            }
        }

        byte[] bytes = sw.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "examen-" + exam.getTitle().replaceAll("[^a-zA-Z0-9]", "_") + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    /** Evita CSV injection (fórmules Excel =, +, -, @) */
    static String sanitizeCsvCell(String value) {
        if (value == null) return "";
        String v = value.strip();
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            return "'" + v;
        }
        return v;
    }
}

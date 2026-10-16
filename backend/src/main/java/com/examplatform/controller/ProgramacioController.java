package com.examplatform.controller;

import com.examplatform.domain.model.User;
import com.examplatform.domain.service.AuditLogService;
import com.examplatform.domain.service.ProgramacioService;
import com.examplatform.dto.ExamDto;
import com.examplatform.dto.ProgramacioDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Competències i unitats de treball d'un mòdul, i unitats que avalua cada examen. */
@RestController
@RequiredArgsConstructor
public class ProgramacioController {

    private final ProgramacioService programacioService;
    private final AuditLogService auditLog;

    @GetMapping("/api/moduls/{modulId}/programacio")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ProgramacioDto obte(@PathVariable UUID modulId, @AuthenticationPrincipal User user) {
        return programacioService.obte(modulId, user);
    }

    @PutMapping("/api/moduls/{modulId}/programacio")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ProgramacioDto substitueix(@PathVariable UUID modulId, @RequestBody ProgramacioDto.Peticio req,
                                      @AuthenticationPrincipal User user) {
        ProgramacioDto res = programacioService.substitueix(modulId, req, user);
        auditLog.log(user.getId(), "PROGRAMACIO_UPDATED", "mòdul " + modulId + ": " + res.competencies().size()
                + " competències, " + res.unitats().size() + " unitats");
        return res;
    }

    /** CSV amb una fila per parella (unitat; competència; descripció; nom de la unitat): substitueix tota la programació. */
    @PostMapping(value = "/api/moduls/{modulId}/programacio/csv", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ProgramacioDto importaCsv(@PathVariable UUID modulId, @RequestParam("file") MultipartFile file,
                                     @AuthenticationPrincipal User user) throws IOException {
        if (file.getSize() > 1024 * 1024) throw new IllegalArgumentException("El CSV és massa gran (màxim 1 MB)");
        ProgramacioDto res = programacioService.importaCsv(modulId, new String(file.getBytes(), StandardCharsets.UTF_8), user);
        auditLog.log(user.getId(), "PROGRAMACIO_IMPORTED", "mòdul " + modulId + ": " + res.competencies().size()
                + " competències, " + res.unitats().size() + " unitats");
        return res;
    }

    @PutMapping("/api/exams/{examId}/unitats")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto assignaUnitats(@PathVariable UUID examId, @RequestBody ProgramacioDto.UnitatsExamen req,
                                  @AuthenticationPrincipal User user) {
        ExamDto res = programacioService.assignaUnitats(examId, req.unitatIds(), user);
        auditLog.log(user.getId(), "EXAM_UNITATS_SET", "examen " + examId + ": " + res.unitats().size() + " unitats");
        return res;
    }

    @PutMapping("/api/exams/{examId}/competencies")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ExamDto fixaCompetencies(@PathVariable UUID examId, @RequestBody ProgramacioDto.CompetenciesDeLExamen req,
                                    @AuthenticationPrincipal User user) {
        ExamDto res = programacioService.fixaCompetencies(examId, req.codis(), user);
        auditLog.log(user.getId(), "EXAM_COMPETENCIES_SET", "examen " + examId + ": " + res.competenciesExamen());
        return res;
    }
}

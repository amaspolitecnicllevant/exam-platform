package com.examplatform.dto;

import com.examplatform.domain.service.CompetenciesExamen;
import com.examplatform.domain.service.Numeracio;
import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.ExamStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record ExamDto(
        UUID id,
        String title,
        int durada,
        String instruccions,
        ExamStatus status,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime scheduledAt,
        UUID scheduledGrupId,
        String scheduledGrupName,
        BigDecimal penalitzacioChoice,
        UUID modulId,
        String modulNom,
        String cicleNom,
        String departamentNom,
        UUID aulaId,
        String aulaNom,
        String aulaCidr,
        boolean notesVisibles,
        boolean unaPreguntaPerPantalla,
        boolean restringit,
        boolean teSessions,
        /** Quan un alumne va començar l'examen per primera vegada (null si encara no l'ha començat ningú). */
        LocalDateTime primeraSessio,
        /** UT que avalua l'examen (buit = sense restricció de competències). */
        List<ProgramacioDto.Unitat> unitats,
        /** Totes les competències del mòdul de l'examen, amb la descripció. */
        List<ProgramacioDto.Competencia> competencies,
        /** Competències que declara l'examen (codis lliures, ordenats). */
        List<String> competenciesExamen,
        /** Avisos sobre competències que no són a la programació (només per al professorat; no bloquegen res). */
        List<String> avisos,
        List<QuestionDto> questions
) {
    /** Còpia amb la marca «ja té sessions d'alumnes» i la data de la primera (només les calcula el llistat del professor). */
    public ExamDto ambSessions(boolean teSessions, LocalDateTime primeraSessio) {
        return new ExamDto(id, title, durada, instruccions, status, createdByName, createdAt, scheduledAt,
                scheduledGrupId, scheduledGrupName, penalitzacioChoice, modulId, modulNom, cicleNom, departamentNom, aulaId,
                aulaNom, aulaCidr, notesVisibles, unaPreguntaPerPantalla, restringit, teSessions, primeraSessio, unitats, competencies, competenciesExamen, avisos, questions);
    }

    public static ExamDto from(Exam e, boolean includeAnswers) {
        return from(e, includeAnswers, Map.of());
    }

    public static ExamDto from(Exam e, boolean includeAnswers,
                                Map<UUID, List<QuestionFileDto>> filesMap) {
        Map<UUID, Integer> numeros = Numeracio.numeros(e.getQuestions());
        List<QuestionDto> qs = e.getQuestions().stream()
                .map(q -> {
                    List<QuestionFileDto> files = filesMap.getOrDefault(q.getId(), List.of());
                    Integer numero = numeros.get(q.getId());
                    return includeAnswers ? QuestionDto.from(q, files, numero) : QuestionDto.forStudent(q, files, numero);
                })
                .toList();
        return new ExamDto(e.getId(), e.getTitle(), e.getDurada(), e.getInstruccions(),
                e.getStatus(), e.getCreatedBy().getName(), e.getCreatedAt(),
                e.getScheduledAt(),
                e.getScheduledGrup() != null ? e.getScheduledGrup().getId() : null,
                e.getScheduledGrup() != null ? e.getScheduledGrup().getName() : null,
                e.getPenalitzacioChoice() != null ? e.getPenalitzacioChoice() : BigDecimal.ZERO,
                e.getModul() != null ? e.getModul().getId() : null,
                e.getModul() != null ? e.getModul().getNom() : null,
                e.getModul() != null ? e.getModul().getCicle().getNom() : null,
                e.getModul() != null && e.getModul().getCicle().getDepartament() != null
                        ? e.getModul().getCicle().getDepartament().getNom() : null,
                e.getAula() != null ? e.getAula().getId() : null,
                e.getAula() != null ? e.getAula().getNom() : null,
                e.getAula() != null ? e.getAula().getXarxaCidr() : null,
                e.isNotesVisibles(),
                e.isUnaPreguntaPerPantalla(),
                e.isRestringit(),
                false,
                null,
                e.getUnitats().stream().sorted(java.util.Comparator.comparingInt(u -> u.getOrdre()))
                        .map(ProgramacioDto.Unitat::de).toList(),
                e.getModul() == null ? List.of()
                        : e.getModul().getCompetencies().stream().map(ProgramacioDto.Competencia::de).toList(),
                e.getCompetenciesExamen().stream().sorted(CompetenciesExamen.ORDRE_CODIS).toList(),
                includeAnswers ? CompetenciesExamen.avisos(e) : List.of(),
                qs);
    }
}

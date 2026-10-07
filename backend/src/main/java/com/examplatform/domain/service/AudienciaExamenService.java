package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.AlumneAccesDto;
import com.examplatform.infrastructure.persistence.ExamRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import com.examplatform.infrastructure.persistence.MatriculaRepository;
import com.examplatform.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Qui pot fer un examen restringit: els alumnes amb una sessió assignada. El professor els tria en
 * publicar i en pot afegir o treure després (p. ex. l'alumne que va faltar i ho fa un altre dia; el seu
 * rellotge comença quan obre l'examen). No depèn d'{@link ExamService}: aquest l'usa en publicar.
 */
@Service
@RequiredArgsConstructor
public class AudienciaExamenService {

    private final ExamSessionRepository sessionRepository;
    private final MatriculaRepository matriculaRepository;
    private final UserRepository userRepository;
    private final ExamRepository examRepository;

    /**
     * Crea la sessió pendent dels alumnes que encara no en tenen i torna quants n'ha creat. Han de ser
     * alumnes i, si l'examen té mòdul, estar-hi matriculats (de qualsevol curs: una recuperació pot ser
     * del curs anterior). Si algun no és vàlid no es fa res.
     */
    @Transactional
    public int assigna(Exam exam, Collection<UUID> alumneIds) {
        if (alumneIds == null || alumneIds.isEmpty()) throw new IllegalArgumentException("Tria almenys un alumne");
        Set<UUID> ids = new LinkedHashSet<>(alumneIds);
        Map<UUID, User> alumnes = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> alumnes.put(u.getId(), u));
        for (UUID id : ids) {
            User u = alumnes.get(id);
            if (u == null || u.getRole() != Role.STUDENT) throw new IllegalArgumentException("Només es poden assignar alumnes");
        }
        if (exam.getModul() != null) {
            Set<UUID> matriculats = new HashSet<>(matriculaRepository.findAlumneIdsByModulId(exam.getModul().getId()));
            List<String> noMatriculats = ids.stream().filter(id -> !matriculats.contains(id))
                    .map(id -> alumnes.get(id).getName()).sorted().toList();
            if (!noMatriculats.isEmpty()) {
                throw new IllegalArgumentException("No estan matriculats al mòdul de l'examen: " + String.join(", ", noMatriculats));
            }
        }
        Set<UUID> ambSessio = new HashSet<>(sessionRepository.findStudentIdsByExamId(exam.getId()));
        int creades = 0;
        for (UUID id : ids) {
            if (ambSessio.contains(id)) continue;
            sessionRepository.save(ExamSession.builder().exam(exam).student(alumnes.get(id)).build());
            creades++;
        }
        return creades;
    }

    /**
     * Afegeix destinataris des de la gestió d'alumnes. En un esborrany, el primer que s'hi afegeix restringeix
     * l'examen (quan s'activi, només el veuran ells); en un d'actiu, només si ja era restringit.
     */
    @Transactional
    public int afegeix(Exam exam, Collection<UUID> alumneIds) {
        if (exam.getStatus() == ExamStatus.DRAFT) {
            if (exam.getScheduledAt() != null) {
                throw new IllegalStateException("Aquest examen està programat per a un grup: anul·la la programació per triar-ne els alumnes");
            }
            if (!exam.isRestringit()) {
                exam.setRestringit(true);
                examRepository.save(exam);
            }
        } else if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("L'examen ja està tancat");
        } else if (!exam.isRestringit()) {
            throw new IllegalStateException("Aquest examen és per a tots els alumnes del mòdul: no cal afegir-hi ningú");
        }
        return assigna(exam, alumneIds);
    }

    /** Torna un esborrany a «tots els del mòdul»: treu els destinataris que encara no han obert l'examen. */
    @Transactional
    public void tots(Exam exam) {
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new IllegalStateException("Només es pot tornar a «tots» un examen que encara no està actiu");
        }
        sessionRepository.findByExamId(exam.getId()).stream()
                .filter(s -> s.getStartedAt() == null && s.getStatus() != SessionStatus.SUBMITTED)
                .forEach(sessionRepository::delete);
        exam.setRestringit(false);
        examRepository.save(exam);
    }

    @Transactional(readOnly = true)
    public AlumneAccesDto.Llista llista(Exam exam) {
        List<AlumneAccesDto> alumnes = sessionRepository.findByExamIdWithDetails(exam.getId()).stream()
                .map(s -> new AlumneAccesDto(s.getStudent().getId(), s.getStudent().getName(), s.getStudent().getEmail(), estat(s)))
                .sorted(Comparator.comparing(AlumneAccesDto::nom, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new AlumneAccesDto.Llista(exam.isRestringit(), alumnes);
    }

    /** Treu l'accés a un alumne que encara no ha obert l'examen; si ja l'ha començat, es manté. */
    @Transactional
    public void treu(Exam exam, UUID alumneId) {
        ExamSession s = sessionRepository.findByExamIdAndStudentId(exam.getId(), alumneId)
                .orElseThrow(() -> new NoSuchElementException("Aquest alumne no té accés assignat a l'examen"));
        if (s.getStartedAt() != null || s.getStatus() == SessionStatus.SUBMITTED) {
            throw new IllegalStateException("L'alumne ja ha començat l'examen: no se li pot treure l'accés");
        }
        sessionRepository.delete(s);
    }

    private static AlumneAccesDto.Estat estat(ExamSession s) {
        if (s.getStatus() == SessionStatus.SUBMITTED) return AlumneAccesDto.Estat.ENTREGAT;
        return s.getStartedAt() == null ? AlumneAccesDto.Estat.PENDENT : AlumneAccesDto.Estat.EN_CURS;
    }
}

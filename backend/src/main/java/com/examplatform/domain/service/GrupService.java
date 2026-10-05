package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.GrupDto;
import com.examplatform.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Service
@RequiredArgsConstructor
public class GrupService {

    private final GrupRepository     grupRepository;
    private final UserRepository     userRepository;
    private final ExamSessionRepository sessionRepository;
    private final ExamService        examService;
    private final ModulRepository    modulRepository;
    private final MatriculaRepository matriculaRepository;

    @Transactional
    public GrupDto create(String name, User createdBy) {
        Grup grup = Grup.builder()
                .name(name)
                .createdBy(createdBy)
                .build();
        return GrupDto.from(grupRepository.save(grup));
    }

    @Transactional(readOnly = true)
    public List<GrupDto> findAll(User requestingUser) {
        List<Grup> grups = requestingUser.getRole() == Role.ADMIN
                ? grupRepository.findAllWithStudents()
                : grupRepository.findByCreatedByIdWithStudents(requestingUser.getId());
        return grups.stream()
                .sorted(Comparator.comparing(Grup::getName, String.CASE_INSENSITIVE_ORDER))
                .map(GrupDto::from)
                .toList();
    }

    @Transactional
    public GrupDto setStudents(UUID grupId, List<UUID> studentIds, User requestingUser) {
        Grup grup = getAndAssertOwnership(grupId, requestingUser);

        Set<User> students = new HashSet<>(userRepository.findAllById(studentIds));
        // Comprova que tots siguin alumnes
        students.forEach(u -> {
            if (u.getRole() != Role.STUDENT) {
                throw new IllegalArgumentException(
                        u.getEmail() + " no és un alumne");
            }
        });

        grup.setStudents(students);
        return GrupDto.from(grupRepository.save(grup));
    }

    @Transactional
    public void delete(UUID grupId, User requestingUser) {
        Grup grup = getAndAssertOwnership(grupId, requestingUser);
        grupRepository.delete(grup);
    }

    /**
     * Crea una ExamSession per a cada alumne del grup que no en tingui ja una.
     * L'examen ha d'estar PUBLISHED.
     */
    @Transactional
    public int assignExam(UUID grupId, UUID examId, User requestingUser) {
        Grup grup = getAndAssertOwnership(grupId, requestingUser);
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, requestingUser);

        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("L'examen ha d'estar publicat per poder-lo assignar");
        }

        Set<UUID> existingStudents = new HashSet<>(
                sessionRepository.findStudentIdsByExamId(examId));

        // Si l'examen té mòdul, només reben sessió els alumnes matriculats en aquell mòdul (de
        // qualsevol curs, com en començar l'examen: una recuperació pot ser del curs anterior)
        Set<UUID> eligibles = exam.getModul() != null
                ? new HashSet<>(matriculaRepository.findAlumneIdsByModulId(exam.getModul().getId()))
                : null;   // null = tots

        int created = 0;
        for (User student : grup.getStudents()) {
            if (existingStudents.contains(student.getId())) continue;
            if (eligibles != null && !eligibles.contains(student.getId())) continue;
            sessionRepository.save(ExamSession.builder().exam(exam).student(student).build());
            created++;
        }
        return created;
    }

    @Transactional
    public GrupDto assignModul(UUID grupId, UUID modulId, User requestingUser) {
        Grup grup = getAndAssertOwnership(grupId, requestingUser);
        Modul modul = modulRepository.findById(modulId)
                .orElseThrow(() -> new NoSuchElementException("Mòdul no trobat: " + modulId));
        grup.setModul(modul);
        return GrupDto.from(grupRepository.save(grup));
    }


    private Grup getAndAssertOwnership(UUID grupId, User user) {
        Grup grup = grupRepository.findById(grupId)
                .orElseThrow(() -> new NoSuchElementException("Grup no trobat: " + grupId));
        if (user.getRole() != Role.ADMIN
                && !grup.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("No tens permís sobre aquest grup");
        }
        return grup;
    }
}

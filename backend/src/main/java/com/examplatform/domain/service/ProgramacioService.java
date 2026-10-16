package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ExamDto;
import com.examplatform.dto.ProgramacioDto;
import com.examplatform.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Programació d'un mòdul (competències i unitats de treball) i unitats que avalua cada examen.
 * L'avaluació és per competències: les preguntes guarden el codi de la competència (CO1…) i aquí
 * es defineix quines n'hi ha i quines UT les treballen.
 */
@Service
@RequiredArgsConstructor
public class ProgramacioService {

    private static final Pattern CODI = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,19}$");

    private final ModulRepository modulRepository;
    private final CompetenciaRepository competenciaRepository;
    private final UnitatTreballRepository unitatRepository;
    private final QuestionRepository questionRepository;
    private final ExamRepository examRepository;
    private final ImparticioRepository imparticioRepository;
    private final ExamService examService;

    @Transactional(readOnly = true)
    public ProgramacioDto obte(UUID modulId, User user) {
        Modul modul = modul(modulId, user);
        return dto(modul);
    }

    @Transactional
    public ProgramacioDto substitueix(UUID modulId, ProgramacioDto.Peticio req, User user) {
        Modul modul = modul(modulId, user);
        List<ProgramacioDto.CompetenciaPeticio> cos = req.competencies() == null ? List.of() : req.competencies();
        List<ProgramacioDto.UnitatPeticio> uts = req.unitats() == null ? List.of() : req.unitats();

        // Validació de la petició
        Map<String, String> descripcions = new LinkedHashMap<>();
        for (var c : cos) {
            String codi = codi(c.codi(), "competència");
            if (descripcions.put(codi, c.descripcio() == null ? "" : c.descripcio().strip()) != null) {
                throw new IllegalArgumentException("La competència " + codi + " està repetida");
            }
        }
        Set<String> codisUt = new LinkedHashSet<>();
        for (var u : uts) {
            String codi = codi(u.codi(), "unitat");
            if (!codisUt.add(codi)) throw new IllegalArgumentException("La unitat " + codi + " està repetida");
            for (String c : u.competencies() == null ? List.<String>of() : u.competencies()) {
                if (!descripcions.containsKey(c.strip().toUpperCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("La unitat " + codi + " fa servir la competència " + c + ", que no existeix");
                }
            }
        }

        // Competències: es conserva l'id de les que ja hi eren (per codi); no se'n pot treure cap que tingui preguntes
        Map<String, Competencia> actuals = new HashMap<>();
        competenciaRepository.findByModulIdOrderByOrdreAsc(modulId).forEach(c -> actuals.put(c.getCodi(), c));
        for (Competencia c : actuals.values()) {
            if (!descripcions.containsKey(c.getCodi()) && questionRepository.comptaCompetencia(modulId, c.getCodi()) > 0) {
                throw new IllegalArgumentException("No es pot treure la competència " + c.getCodi()
                        + ": hi ha preguntes d'exàmens d'aquest mòdul que l'utilitzen");
            }
        }
        Map<String, Competencia> finals = new LinkedHashMap<>();
        int ordre = 0;
        for (var e : descripcions.entrySet()) {
            Competencia c = actuals.remove(e.getKey());
            if (c == null) c = Competencia.builder().modul(modul).codi(e.getKey()).build();
            c.setDescripcio(e.getValue());
            c.setOrdre(ordre++);
            finals.put(e.getKey(), c);
        }

        // Unitats: tampoc se'n pot treure que algun examen avaluï
        Map<String, UnitatTreball> unitatsActuals = new HashMap<>();
        unitatRepository.findByModulIdOrderByOrdreAsc(modulId).forEach(u -> unitatsActuals.put(u.getCodi(), u));
        for (UnitatTreball u : unitatsActuals.values()) {
            if (!codisUt.contains(u.getCodi()) && examRepository.comptaPerUnitat(u.getId()) > 0) {
                throw new IllegalArgumentException("No es pot treure la unitat " + u.getCodi()
                        + ": hi ha exàmens que l'avaluen");
            }
        }

        competenciaRepository.saveAll(finals.values());
        List<UnitatTreball> desades = new ArrayList<>();
        int ordreUt = 0;
        for (var u : uts) {
            String codi = codi(u.codi(), "unitat");
            UnitatTreball ut = unitatsActuals.remove(codi);
            if (ut == null) ut = UnitatTreball.builder().modul(modul).codi(codi).build();
            ut.setNom(u.nom() == null ? "" : u.nom().strip());
            ut.setOrdre(ordreUt++);
            Set<Competencia> deLaUnitat = new LinkedHashSet<>();
            for (String c : u.competencies() == null ? List.<String>of() : u.competencies()) {
                deLaUnitat.add(finals.get(c.strip().toUpperCase(Locale.ROOT)));
            }
            ut.setCompetencies(deLaUnitat);
            desades.add(ut);
        }
        unitatRepository.saveAll(desades);
        unitatRepository.deleteAll(unitatsActuals.values());
        competenciaRepository.deleteAll(actuals.values());
        unitatRepository.flush();
        return dto(modul);
    }

    @Transactional
    public ProgramacioDto importaCsv(UUID modulId, String csv, User user) {
        return substitueix(modulId, ProgramacioCsvParser.llegeix(csv), user);
    }

    /** Fixa les UT que avalua l'examen (totes del mòdul de l'examen); buit = sense restricció. */
    @Transactional
    public ExamDto assignaUnitats(UUID examId, List<UUID> unitatIds, User user) {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, user);
        if (exam.getModul() == null) {
            throw new IllegalArgumentException("Assigna primer el mòdul a l'examen");
        }
        Set<UnitatTreball> triades = new LinkedHashSet<>();
        for (UUID id : unitatIds == null ? List.<UUID>of() : unitatIds) {
            UnitatTreball u = unitatRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Unitat no trobada: " + id));
            if (!u.getModul().getId().equals(exam.getModul().getId())) {
                throw new IllegalArgumentException("La unitat " + u.getCodi() + " no és del mòdul de l'examen");
            }
            triades.add(u);
        }
        exam.getUnitats().clear();
        exam.getUnitats().addAll(triades);
        return ExamDto.from(examRepository.save(exam), true);
    }

    /** Fixa les competències que avalua l'examen (codis lliures; buit = les de les UT o del mòdul). */
    @Transactional
    public ExamDto fixaCompetencies(UUID examId, List<String> codis, User user) {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, user);
        Set<String> nous = new LinkedHashSet<>();
        for (String c : codis == null ? List.<String>of() : codis) {
            if (c != null && !c.isBlank()) nous.add(codi(c, "competència"));
        }
        exam.getCompetenciesExamen().clear();
        exam.getCompetenciesExamen().addAll(nous);
        return ExamDto.from(examRepository.save(exam), true);
    }

    private Modul modul(UUID modulId, User user) {
        Modul modul = modulRepository.findById(modulId)
                .orElseThrow(() -> new NoSuchElementException("Mòdul no trobat: " + modulId));
        if (user.getRole() != Role.ADMIN
                && !(user.getRole() == Role.PROFESSOR && imparticioRepository.professorImparteixModul(user.getId(), modulId))) {
            throw new AccessDeniedException("No tens cap impartició per al mòdul " + modul.getCodi());
        }
        return modul;
    }

    private ProgramacioDto dto(Modul modul) {
        return new ProgramacioDto(modul.getId(),
                competenciaRepository.findByModulIdOrderByOrdreAsc(modul.getId()).stream()
                        .map(ProgramacioDto.Competencia::de).toList(),
                unitatRepository.findByModulIdOrderByOrdreAsc(modul.getId()).stream()
                        .map(ProgramacioDto.Unitat::de).toList());
    }

    private static String codi(String brut, String que) {
        String codi = brut == null ? "" : brut.strip().toUpperCase(Locale.ROOT);
        if (!CODI.matcher(codi).matches()) {
            throw new IllegalArgumentException("El codi de " + que + " «" + (brut == null ? "" : brut)
                    + "» no és vàlid: fins a 20 caràcters entre lletres, números, punt, guió i guió baix");
        }
        return codi;
    }
}

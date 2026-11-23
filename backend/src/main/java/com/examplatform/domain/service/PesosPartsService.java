package com.examplatform.domain.service;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;
import com.examplatform.infrastructure.persistence.ExamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Pes (%) de cada part (secció «###») de l'examen sobre la nota total, modificable un cop fet l'examen.
 *
 * <p>No es toquen els punts de les preguntes ni les notes de les respostes: només es desa el pes a la secció i
 * {@link Puntuacio} escala els punts de la part perquè valgui aquest percentatge. Treure els pesos torna a la
 * puntuació original.
 */
@Service
@RequiredArgsConstructor
public class PesosPartsService {

    private static final BigDecimal CENT = new BigDecimal("100");
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    private final ExamRepository examRepository;

    /** Pes d'una secció, per al canvi. */
    public record PesPart(UUID seccioId, BigDecimal pes) {}

    /** Fixa el pes de TOTES les parts (han de sumar 100). Retorna el resum de canvis per a l'auditoria. */
    @Transactional
    public String fixa(Exam exam, List<PesPart> pesos) {
        List<Question> seccions = seccions(exam);
        if (seccions.isEmpty()) throw new IllegalArgumentException("L'examen no té parts (seccions)");
        if (hiHaPreguntesAbansDeLaPrimeraSeccio(exam)) {
            throw new IllegalArgumentException("Hi ha preguntes abans de la primera part: cal posar-les dins una part per poder repartir els pesos");
        }
        Map<UUID, BigDecimal> demanats = new HashMap<>();
        for (PesPart p : pesos == null ? List.<PesPart>of() : pesos) {
            if (p.seccioId() == null || p.pes() == null) throw new IllegalArgumentException("Pes de part no vàlid");
            if (demanats.put(p.seccioId(), p.pes()) != null) throw new IllegalArgumentException("Part repetida");
        }
        Set<UUID> idsExamen = new HashSet<>();
        BigDecimal suma = BigDecimal.ZERO;
        for (Question s : seccions) {
            idsExamen.add(s.getId());
            BigDecimal pes = demanats.get(s.getId());
            if (pes == null) throw new IllegalArgumentException("Falta el pes de la part «" + s.getEnunciat() + "»");
            if (pes.signum() < 0 || pes.compareTo(CENT) > 0) {
                throw new IllegalArgumentException("El pes de «" + s.getEnunciat() + "» ha d'estar entre 0 i 100");
            }
            if (pes.signum() > 0 && puntsDeLaPart(exam, s).signum() <= 0) {
                throw new IllegalArgumentException("La part «" + s.getEnunciat() + "» no té preguntes amb punts");
            }
            suma = suma.add(pes);
        }
        if (!idsExamen.containsAll(demanats.keySet())) throw new IllegalArgumentException("Hi ha parts que no són d'aquest examen");
        if (suma.subtract(CENT).abs().compareTo(TOLERANCIA) > 0) {
            throw new IllegalArgumentException("Els pesos han de sumar 100 (ara sumen " + suma.stripTrailingZeros().toPlainString() + ")");
        }
        StringBuilder resum = new StringBuilder();
        for (Question s : seccions) {
            BigDecimal pes = demanats.get(s.getId());
            s.setPesPart(pes);
            resum.append(resum.length() == 0 ? "" : "; ").append(s.getEnunciat()).append("=")
                    .append(pes.stripTrailingZeros().toPlainString()).append("%");
        }
        examRepository.save(exam);
        return resum.toString();
    }

    /** Treu els pesos: l'examen torna a puntuar amb els punts originals de cada pregunta. */
    @Transactional
    public void treu(Exam exam) {
        seccions(exam).forEach(s -> s.setPesPart(null));
        examRepository.save(exam);
    }

    private static List<Question> seccions(Exam exam) {
        return exam.getQuestions().stream().filter(q -> q.getTipus() == QuestionType.SECTION)
                .sorted(Comparator.comparingInt(Question::getOrdre)).toList();
    }

    private static boolean hiHaPreguntesAbansDeLaPrimeraSeccio(Exam exam) {
        return exam.getQuestions().stream().min(Comparator.comparingInt(Question::getOrdre))
                .map(primera -> primera.getTipus() != QuestionType.SECTION).orElse(false);
    }

    private static BigDecimal puntsDeLaPart(Exam exam, Question seccio) {
        BigDecimal suma = BigDecimal.ZERO;
        boolean dins = false;
        for (Question q : exam.getQuestions().stream().sorted(Comparator.comparingInt(Question::getOrdre)).toList()) {
            if (q.getTipus() == QuestionType.SECTION) dins = q.getId().equals(seccio.getId());
            else if (dins) suma = suma.add(q.getPunts());
        }
        return suma;
    }
}

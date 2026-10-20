package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ResultatsModulDto;
import com.examplatform.dto.ResultatsModulDto.CompetenciaDeUnitat;
import com.examplatform.dto.ResultatsModulDto.Examen;
import com.examplatform.dto.ResultatsModulDto.ExamenDeCompetencia;
import com.examplatform.dto.ResultatsModulDto.Nota;
import com.examplatform.dto.ResultatsModulDto.Unitat;
import com.examplatform.dto.ResultatsModulDto.UnitatDeCompetencia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Càlcul de les notes mitjanes d'un mòdul per unitat de treball i per competència. Funció pura: rep els exàmens amb
 * les respostes dels alumnes que han entregat i no toca la BD (així es prova sense base de dades).
 *
 * <p>Una nota agrupada és sempre {@code suma de punts mitjans / suma de punts màxims}: cada examen pesa segons els
 * punts que té en aquella unitat o competència.
 */
public final class ResultatsModul {

    private static final BigDecimal CENT = new BigDecimal("100");

    private ResultatsModul() {}

    /** Un examen amb les respostes (per pregunta) de cada alumne que l'ha entregat. */
    public record ExamenEntregat(Exam exam, List<Map<UUID, Answer>> alumnes) {}

    /** Punts mitjans i màxims d'un examen per a un conjunt de preguntes. */
    private record Punts(BigDecimal mitjana, BigDecimal maxim) {}

    private record Dades(Exam exam, int entregats, Punts total, Map<String, Punts> perCompetencia) {}

    public static ResultatsModulDto calcula(Modul modul, List<UnitatTreball> unitats, List<ExamenEntregat> examens) {
        List<Dades> dades = new ArrayList<>();
        int pendents = 0;
        for (ExamenEntregat e : examens) {
            if (e.alumnes().isEmpty()) continue;
            List<Question> preguntes = e.exam().getQuestions().stream()
                    .filter(q -> q.getTipus() != QuestionType.SECTION)
                    .sorted(Comparator.comparingInt(Question::getOrdre)).toList();
            for (Map<UUID, Answer> seves : e.alumnes()) {
                for (Question q : preguntes) if (Puntuacio.punts(q, seves.get(q.getId())) == null) pendents++;
            }
            Map<String, List<Question>> perCo = new TreeMap<>(CODIS);
            for (Question q : preguntes) {
                if (q.getCompetencia() != null && !q.getCompetencia().isBlank()) {
                    perCo.computeIfAbsent(q.getCompetencia().strip(), k -> new ArrayList<>()).add(q);
                }
            }
            Map<String, Punts> punts = new LinkedHashMap<>();
            perCo.forEach((co, qs) -> punts.put(co, mitjana(qs, e.alumnes())));
            dades.add(new Dades(e.exam(), e.alumnes().size(), mitjana(preguntes, e.alumnes()), punts));
        }

        List<Unitat> resUnitats = new ArrayList<>();
        for (UnitatTreball u : unitats) {
            List<Dades> seus = dades.stream().filter(d -> d.exam().getUnitats().stream().anyMatch(x -> x.getId().equals(u.getId()))).toList();
            resUnitats.add(new Unitat(u.getCodi(), u.getNom(),
                    seus.isEmpty() ? null : nota(seus.stream().map(Dades::total).toList()),
                    seus.stream().map(d -> new Examen(d.exam().getId(), d.exam().getTitle(), d.entregats(), nota(List.of(d.total())))).toList(),
                    competenciesDe(seus)));
        }

        List<ResultatsModulDto.Competencia> resCompetencies = new ArrayList<>();
        for (com.examplatform.domain.model.Competencia c : modul.getCompetencies().stream()
                .sorted(Comparator.comparingInt(com.examplatform.domain.model.Competencia::getOrdre)).toList()) {
            List<Dades> amb = dades.stream().filter(d -> d.perCompetencia().containsKey(c.getCodi())).toList();
            List<UnitatDeCompetencia> perUnitat = new ArrayList<>();
            for (UnitatTreball u : unitats) {
                List<Dades> seus = amb.stream().filter(d -> d.exam().getUnitats().stream().anyMatch(x -> x.getId().equals(u.getId()))).toList();
                if (!seus.isEmpty()) perUnitat.add(new UnitatDeCompetencia(u.getCodi(), u.getNom(), examensDeCompetencia(seus, c.getCodi())));
            }
            List<Dades> senseUnitat = amb.stream().filter(d -> d.exam().getUnitats().isEmpty()).toList();
            if (!senseUnitat.isEmpty()) perUnitat.add(new UnitatDeCompetencia(null, "Exàmens sense unitat assignada", examensDeCompetencia(senseUnitat, c.getCodi())));
            resCompetencies.add(new ResultatsModulDto.Competencia(c.getCodi(), c.getDescripcio(),
                    amb.isEmpty() ? null : nota(amb.stream().map(d -> d.perCompetencia().get(c.getCodi())).toList()), perUnitat));
        }
        return new ResultatsModulDto(modul.getId(), dades.size(), pendents, resUnitats, resCompetencies);
    }

    private static List<CompetenciaDeUnitat> competenciesDe(List<Dades> exams) {
        Set<String> codis = new TreeSet<>(CODIS);
        exams.forEach(d -> codis.addAll(d.perCompetencia().keySet()));
        List<CompetenciaDeUnitat> res = new ArrayList<>();
        for (String co : codis) {
            res.add(new CompetenciaDeUnitat(co, nota(exams.stream().map(d -> d.perCompetencia().get(co)).filter(Objects::nonNull).toList())));
        }
        return res;
    }

    private static List<ExamenDeCompetencia> examensDeCompetencia(List<Dades> exams, String co) {
        BigDecimal totalMaxim = exams.stream().map(d -> d.perCompetencia().get(co).maxim()).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ExamenDeCompetencia> res = new ArrayList<>();
        for (Dades d : exams) {
            Punts p = d.perCompetencia().get(co);
            res.add(new ExamenDeCompetencia(d.exam().getId(), d.exam().getTitle(), d.entregats(), nota(List.of(p)),
                    percent(p.mitjana(), p.maxim()), percent(p.maxim(), totalMaxim)));
        }
        return res;
    }

    /** Punts mitjans de l'examen (tots els alumnes) i màxims per a un conjunt de preguntes. */
    private static Punts mitjana(List<Question> preguntes, List<Map<UUID, Answer>> alumnes) {
        BigDecimal suma = BigDecimal.ZERO;
        for (Map<UUID, Answer> seves : alumnes) suma = suma.add(Puntuacio.total(preguntes, seves));
        return new Punts(suma.divide(BigDecimal.valueOf(alumnes.size()), 4, RoundingMode.HALF_UP), Puntuacio.maxim(preguntes));
    }

    /** Agrupa: suma de punts mitjans entre suma de punts màxims. */
    private static Nota nota(List<Punts> punts) {
        BigDecimal mitjana = punts.stream().map(Punts::mitjana).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal maxim = punts.stream().map(Punts::maxim).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Nota(mitjana.setScale(2, RoundingMode.HALF_UP), maxim.setScale(2, RoundingMode.HALF_UP),
                Puntuacio.sobreDeu(mitjana, maxim));
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO : part.multiply(CENT).divide(total, 1, RoundingMode.HALF_UP);
    }

    /** CO2 abans que CO10. */
    private static final Comparator<String> CODIS =
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());
}

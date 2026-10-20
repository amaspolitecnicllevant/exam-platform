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
import java.util.function.Function;

/**
 * Càlcul de les notes mitjanes d'un mòdul per unitat de treball i per competència. Funció pura: rep els exàmens amb
 * les respostes dels alumnes que han entregat i no toca la BD (així es prova sense base de dades).
 *
 * <p>Exàmens i pràctiques es calculen per separat: cada part és {@code suma de punts mitjans / suma de punts màxims}
 * (un examen pesa segons els seus punts). Si la unitat té un % d'exàmens definit, la nota de la unitat és
 * {@code %exàmens · nota dels exàmens + %pràctiques · nota de les pràctiques}; si només hi ha una part, aquesta compta
 * el 100 %. Sense % definit, tot compta junt pels punts.
 */
public final class ResultatsModul {

    private static final BigDecimal CENT = new BigDecimal("100");

    private ResultatsModul() {}

    /** Un examen amb les respostes (per pregunta) de cada alumne que l'ha entregat. */
    public record ExamenEntregat(Exam exam, List<Map<UUID, Answer>> alumnes) {}

    /** Punts mitjans i màxims d'un examen per a un conjunt de preguntes. */
    private record Punts(BigDecimal mitjana, BigDecimal maxim) {}

    private record Dades(Exam exam, int entregats, Punts total, Map<String, Punts> perCompetencia) {
        boolean esExamen() { return exam.getTipus() != TipusExamen.PRACTICA; }
    }

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
            List<Dades> seus = dadesDeLaUnitat(dades, u);
            Function<Dades, Punts> total = Dades::total;
            List<Dades> examensUt = seus.stream().filter(Dades::esExamen).toList();
            List<Dades> practiques = seus.stream().filter(d -> !d.esExamen()).toList();
            int[] pesos = pesosAplicats(u.getPesExamens(), !examensUt.isEmpty(), !practiques.isEmpty());
            resUnitats.add(new Unitat(u.getCodi(), u.getNom(), u.getPesExamens(),
                    seus.isEmpty() ? null : nota(seus, u.getPesExamens(), total),
                    examensUt.isEmpty() ? null : nota(examensUt, null, total),
                    practiques.isEmpty() ? null : nota(practiques, null, total),
                    seus.stream().map(d -> new Examen(d.exam().getId(), d.exam().getTitle(), d.exam().getTipus(), d.entregats(),
                            nota(List.of(d), null, total), pes(d, seus, pesos, total))).toList(),
                    competenciesDe(seus, u.getPesExamens())));
        }

        List<ResultatsModulDto.Competencia> resCompetencies = new ArrayList<>();
        for (com.examplatform.domain.model.Competencia c : modul.getCompetencies().stream()
                .sorted(Comparator.comparingInt(com.examplatform.domain.model.Competencia::getOrdre)).toList()) {
            Function<Dades, Punts> deLaCo = d -> d.perCompetencia().get(c.getCodi());
            List<Dades> amb = dades.stream().filter(d -> deLaCo.apply(d) != null).toList();
            List<UnitatDeCompetencia> perUnitat = new ArrayList<>();
            List<Nota> notesUnitats = new ArrayList<>();
            List<BigDecimal> pesosUnitats = new ArrayList<>();
            boolean hiHaPercentatges = false;
            for (UnitatTreball u : unitats) {
                List<Dades> seus = dadesDeLaUnitat(amb, u);
                if (seus.isEmpty()) continue;
                int[] pesos = pesosAplicats(u.getPesExamens(), seus.stream().anyMatch(Dades::esExamen), seus.stream().anyMatch(d -> !d.esExamen()));
                hiHaPercentatges |= pesos != null;
                perUnitat.add(new UnitatDeCompetencia(u.getCodi(), u.getNom(), examensDeCompetencia(seus, pesos, deLaCo)));
                notesUnitats.add(nota(seus, u.getPesExamens(), deLaCo));
                pesosUnitats.add(seus.stream().map(d -> deLaCo.apply(d).maxim()).reduce(BigDecimal.ZERO, BigDecimal::add));
            }
            List<Dades> senseUnitat = amb.stream().filter(d -> d.exam().getUnitats().isEmpty()).toList();
            if (!senseUnitat.isEmpty()) {
                perUnitat.add(new UnitatDeCompetencia(null, "Exàmens sense unitat assignada", examensDeCompetencia(senseUnitat, null, deLaCo)));
                notesUnitats.add(nota(senseUnitat, null, deLaCo));
                pesosUnitats.add(senseUnitat.stream().map(d -> deLaCo.apply(d).maxim()).reduce(BigDecimal.ZERO, BigDecimal::add));
            }
            Nota global;
            if (amb.isEmpty()) global = null;
            else if (!hiHaPercentatges) global = nota(amb, null, deLaCo);   // tot junt pels punts
            else global = mitjanaPonderada(notesUnitats, pesosUnitats);     // les unitats pesen pels punts de la competència
            resCompetencies.add(new ResultatsModulDto.Competencia(c.getCodi(), c.getDescripcio(), global, perUnitat));
        }
        return new ResultatsModulDto(modul.getId(), dades.size(), pendents, resUnitats, resCompetencies);
    }

    private static List<Dades> dadesDeLaUnitat(List<Dades> dades, UnitatTreball u) {
        return dades.stream().filter(d -> d.exam().getUnitats().stream().anyMatch(x -> x.getId().equals(u.getId()))).toList();
    }

    // ── Pesos ─────────────────────────────────────────────────────────────────

    /**
     * Percentatges {exàmens, pràctiques} que s'apliquen realment, o null si la unitat no en té de definits.
     * Si només hi ha una de les dues parts, aquesta compta el 100 %.
     */
    static int[] pesosAplicats(Integer pesExamens, boolean hiHaExamens, boolean hiHaPractiques) {
        if (pesExamens == null) return null;
        if (hiHaExamens && !hiHaPractiques) return new int[]{100, 0};
        if (!hiHaExamens && hiHaPractiques) return new int[]{0, 100};
        return new int[]{pesExamens, 100 - pesExamens};
    }

    /** % de la nota (de la unitat o de la competència a la unitat) que representa un examen: el de la seva part pels seus punts. */
    private static BigDecimal pes(Dades d, List<Dades> grup, int[] pesos, Function<Dades, Punts> punts) {
        BigDecimal meu = punts.apply(d).maxim();
        if (pesos == null) {
            BigDecimal total = grup.stream().map(x -> punts.apply(x).maxim()).reduce(BigDecimal.ZERO, BigDecimal::add);
            return percent(meu, total);
        }
        BigDecimal maximPart = grup.stream().filter(x -> x.esExamen() == d.esExamen())
                .map(x -> punts.apply(x).maxim()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pesPart = BigDecimal.valueOf(d.esExamen() ? pesos[0] : pesos[1]);
        return maximPart.signum() == 0 ? BigDecimal.ZERO
                : pesPart.multiply(meu).divide(maximPart, 1, RoundingMode.HALF_UP);
    }

    private static List<CompetenciaDeUnitat> competenciesDe(List<Dades> exams, Integer pesExamens) {
        Set<String> codis = new TreeSet<>(CODIS);
        exams.forEach(d -> codis.addAll(d.perCompetencia().keySet()));
        List<CompetenciaDeUnitat> res = new ArrayList<>();
        for (String co : codis) {
            Function<Dades, Punts> deLaCo = d -> d.perCompetencia().get(co);
            res.add(new CompetenciaDeUnitat(co, nota(exams.stream().filter(d -> deLaCo.apply(d) != null).toList(), pesExamens, deLaCo)));
        }
        return res;
    }

    private static List<ExamenDeCompetencia> examensDeCompetencia(List<Dades> exams, int[] pesos, Function<Dades, Punts> deLaCo) {
        List<ExamenDeCompetencia> res = new ArrayList<>();
        for (Dades d : exams) {
            Punts p = deLaCo.apply(d);
            res.add(new ExamenDeCompetencia(d.exam().getId(), d.exam().getTitle(), d.exam().getTipus(), d.entregats(),
                    nota(List.of(d), null, deLaCo), percent(p.mitjana(), p.maxim()), pes(d, exams, pesos, deLaCo)));
        }
        return res;
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    /** Punts mitjans de l'examen (tots els alumnes) i màxims per a un conjunt de preguntes. */
    private static Punts mitjana(List<Question> preguntes, List<Map<UUID, Answer>> alumnes) {
        BigDecimal suma = BigDecimal.ZERO;
        for (Map<UUID, Answer> seves : alumnes) suma = suma.add(Puntuacio.total(preguntes, seves));
        return new Punts(suma.divide(BigDecimal.valueOf(alumnes.size()), 4, RoundingMode.HALF_UP), Puntuacio.maxim(preguntes));
    }

    /** Nota d'un conjunt d'exàmens: tot junt pels punts, o ponderada exàmens/pràctiques si hi ha percentatge definit. */
    private static Nota nota(List<Dades> exams, Integer pesExamens, Function<Dades, Punts> punts) {
        List<Dades> examensPart = exams.stream().filter(Dades::esExamen).toList();
        List<Dades> practiques = exams.stream().filter(d -> !d.esExamen()).toList();
        int[] pesos = pesosAplicats(pesExamens, !examensPart.isEmpty(), !practiques.isEmpty());
        if (pesos == null) return agrupa(exams.stream().map(punts).toList());
        BigDecimal sobreDeu = BigDecimal.ZERO;
        if (!examensPart.isEmpty()) sobreDeu = sobreDeu.add(agrupa(examensPart.stream().map(punts).toList()).sobreDeu().multiply(BigDecimal.valueOf(pesos[0])));
        if (!practiques.isEmpty()) sobreDeu = sobreDeu.add(agrupa(practiques.stream().map(punts).toList()).sobreDeu().multiply(BigDecimal.valueOf(pesos[1])));
        BigDecimal ponderada = sobreDeu.divide(CENT, 2, RoundingMode.HALF_UP);
        return new Nota(ponderada, BigDecimal.TEN.setScale(2), ponderada);
    }

    /** Agrupa: suma de punts mitjans entre suma de punts màxims. */
    private static Nota agrupa(List<Punts> punts) {
        BigDecimal mitjana = punts.stream().map(Punts::mitjana).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal maxim = punts.stream().map(Punts::maxim).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sobreDeu = Puntuacio.sobreDeu(mitjana, maxim);
        return new Nota(mitjana.setScale(2, RoundingMode.HALF_UP), maxim.setScale(2, RoundingMode.HALF_UP),
                sobreDeu == null ? BigDecimal.ZERO : sobreDeu);
    }

    /** Mitjana de notes sobre 10 ponderada pels pesos (reescalada a 10 punts). */
    private static Nota mitjanaPonderada(List<Nota> notes, List<BigDecimal> pesos) {
        BigDecimal suma = BigDecimal.ZERO, total = BigDecimal.ZERO;
        for (int i = 0; i < notes.size(); i++) {
            suma = suma.add(notes.get(i).sobreDeu().multiply(pesos.get(i)));
            total = total.add(pesos.get(i));
        }
        BigDecimal r = total.signum() == 0 ? BigDecimal.ZERO : suma.divide(total, 2, RoundingMode.HALF_UP);
        return new Nota(r, BigDecimal.TEN.setScale(2), r);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO : part.multiply(CENT).divide(total, 1, RoundingMode.HALF_UP);
    }

    /** CO2 abans que CO10. */
    private static final Comparator<String> CODIS =
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());
}

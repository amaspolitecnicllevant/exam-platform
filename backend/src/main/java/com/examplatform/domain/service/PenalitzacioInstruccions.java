package com.examplatform.domain.service;

import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deduu la penalització de les preguntes de test a partir del text de les instruccions de l'examen, perquè la
 * configuració de l'examen no es contradigui amb el que llegeix l'alumne.
 *
 * <p>Només es mira la frase (separada per «.» o «;») que parla de respostes incorrectes/errades/penalització, i
 * s'accepta, per aquest ordre: una fracció («1/3 de la pregunta»), un percentatge («33%») o uns punts («−0,23 pts»,
 * dividits pels punts de les preguntes de test, que han de valer tots el mateix). Si la frase diu que no resten, o no
 * s'entén, no es dedueix res (queda la penalització per defecte, 0).
 */
public final class PenalitzacioInstruccions {

    private static final Pattern FRASES = Pattern.compile(";|\\.(?=\\s|$)");
    private static final Pattern PARLA_D_ERRORS = Pattern.compile("incorrect|errad|errònia|erroni|errònies|penalitz");
    private static final Pattern NO_RESTA = Pattern.compile("\\bno\\s+(rest|penalitz|descont)|sense\\s+penalitz|cap\\s+penalitz");
    private static final Pattern FRACCIO = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*%");
    private static final Pattern PUNTS = Pattern.compile("(?:[−–-]|\\brest\\w*\\s+)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:pts?|punts?)\\b");

    /** Fraccions habituals a les quals s'arrodoneix una penalització donada en punts (0,03 de 0,10 → 1/3). */
    private static final List<double[]> FRACCIONS_HABITUALS = List.of(
            new double[]{1, 5}, new double[]{1, 4}, new double[]{1, 3}, new double[]{1, 2}, new double[]{1, 1});
    private static final double TOLERANCIA = 0.04;

    private PenalitzacioInstruccions() {}

    /** Penalització (entre 0 exclòs i 1) deduïda de les instruccions, o buit si no n'hi ha o no s'entén. */
    public static Optional<BigDecimal> detecta(String instruccions, Collection<Question> preguntes) {
        if (instruccions == null || instruccions.isBlank()) return Optional.empty();
        BigDecimal puntsTest = puntsComunsDelTest(preguntes);
        for (String frase : FRASES.split(instruccions.toLowerCase(Locale.ROOT))) {
            if (!PARLA_D_ERRORS.matcher(frase).find() || NO_RESTA.matcher(frase).find()) continue;
            Optional<BigDecimal> f = deLaFrase(frase, puntsTest);
            if (f.isPresent()) return f;
        }
        return Optional.empty();
    }

    private static Optional<BigDecimal> deLaFrase(String frase, BigDecimal puntsTest) {
        Matcher fraccio = FRACCIO.matcher(frase);
        if (fraccio.find()) {
            double d = Double.parseDouble(fraccio.group(2));
            if (d > 0) return valid(Double.parseDouble(fraccio.group(1)) / d);
        }
        Matcher percent = PERCENT.matcher(frase);
        if (percent.find()) return valid(nombre(percent.group(1)) / 100);
        Matcher punts = PUNTS.matcher(frase);
        if (punts.find() && puntsTest != null) {
            double f = nombre(punts.group(1)) / puntsTest.doubleValue();
            for (double[] h : FRACCIONS_HABITUALS) {
                if (Math.abs(f - h[0] / h[1]) <= TOLERANCIA) return valid(h[0] / h[1]);
            }
            return valid(f);
        }
        return Optional.empty();
    }

    /** Punts de les preguntes de test si valen tots el mateix; null si no n'hi ha o són diferents. */
    private static BigDecimal puntsComunsDelTest(Collection<Question> preguntes) {
        BigDecimal comu = null;
        for (Question q : preguntes) {
            if (q.getTipus() != QuestionType.CHOICE || q.getPunts() == null) continue;
            if (comu == null) comu = q.getPunts();
            else if (comu.compareTo(q.getPunts()) != 0) return null;
        }
        return comu != null && comu.signum() > 0 ? comu : null;
    }

    private static double nombre(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }

    private static Optional<BigDecimal> valid(double factor) {
        if (!(factor > 0 && factor <= 1)) return Optional.empty();
        return Optional.of(BigDecimal.valueOf(factor).setScale(4, RoundingMode.HALF_UP));
    }
}

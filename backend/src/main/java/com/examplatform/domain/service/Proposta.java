package com.examplatform.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Nota proposada per la correcció automàtica i els motius que la justifiquen.
 * És provisional fins que el professor la revisa (Answer.manualScore).
 */
public record Proposta(BigDecimal score, List<String> motius) {

    public Proposta {
        motius = List.copyOf(motius);
    }

    public static Proposta of(BigDecimal score, String... motius) {
        return new Proposta(score, List.of(motius));
    }

    public String feedback() {
        return String.join("\n", motius);
    }

    /** Format català per a punts: 0.50 → "0,5". */
    public static String pts(BigDecimal value) {
        BigDecimal v = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return v.toPlainString().replace('.', ',');
    }

    /** Motiu de penalització: "−0,5: <motiu>". */
    public static String penalitzacio(BigDecimal punts, String motiu) {
        return "−" + pts(punts) + ": " + motiu;
    }
}

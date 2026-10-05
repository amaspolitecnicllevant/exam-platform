package com.examplatform.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Conceptes clau d'una pregunta de text (bloc {@code :::clau}) i proposta de nota a partir d'ells.
 *
 * <p>Format: una línia per concepte; les alternatives (sinònims) se separen per comes i el pes
 * (en punts) és opcional després de {@code |}. O tots els conceptes tenen pes (i sumen els punts
 * de la pregunta) o cap, i aleshores els punts es reparteixen a parts iguals.
 *
 * <pre>
 * DHCP | 1
 * adreça IP, IP | 0.5
 * </pre>
 *
 * <p>La cerca ignora majúscules, accents i puntuació, i compara paraules senceres
 * ("IP" no coincideix dins de "tipus").
 */
public final class ClausCorreccio {

    public record Clau(List<String> alternatives, BigDecimal pes) {
        public String nom() {
            return alternatives.get(0);
        }
    }

    private final List<Clau> claus;

    private ClausCorreccio(List<Clau> claus) {
        this.claus = List.copyOf(claus);
    }

    public List<Clau> claus() {
        return claus;
    }

    /**
     * Interpreta i valida el bloc {@code :::clau}.
     *
     * @throws IllegalArgumentException si el format no és vàlid
     */
    public static ClausCorreccio parse(String raw, BigDecimal punts) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("el bloc :::clau és buit");
        }
        List<List<String>> alternatives = new ArrayList<>();
        List<BigDecimal> pesos = new ArrayList<>();

        for (String line : raw.lines().map(String::strip).filter(l -> !l.isEmpty()).toList()) {
            String[] parts = line.split("\\|", -1);
            if (parts.length > 2) {
                throw new IllegalArgumentException("massa separadors '|' a la línia «" + line + "»");
            }
            List<String> alts = Arrays.stream(parts[0].split(","))
                    .map(String::strip).filter(a -> !a.isEmpty()).toList();
            if (alts.isEmpty() || alts.stream().allMatch(a -> normalitza(a).isEmpty())) {
                throw new IllegalArgumentException("concepte buit a la línia «" + line + "»");
            }
            BigDecimal pes = null;
            if (parts.length == 2) {
                try {
                    pes = new BigDecimal(parts[1].strip().replace(',', '.'));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("pes no numèric a la línia «" + line + "»");
                }
                if (pes.signum() <= 0) {
                    throw new IllegalArgumentException("el pes ha de ser positiu a la línia «" + line + "»");
                }
            }
            alternatives.add(alts);
            pesos.add(pes);
        }

        long ambPes = pesos.stream().filter(p -> p != null).count();
        if (ambPes != 0 && ambPes != pesos.size()) {
            throw new IllegalArgumentException("o tots els conceptes tenen pes (| punts) o cap");
        }
        if (ambPes == 0) {
            pesos = repartiment(punts, pesos.size());
        } else {
            BigDecimal suma = pesos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (suma.compareTo(punts) != 0) {
                throw new IllegalArgumentException("els pesos sumen " + Proposta.pts(suma)
                        + " i han de sumar els punts de la pregunta (" + Proposta.pts(punts) + ")");
            }
        }

        List<Clau> claus = new ArrayList<>();
        for (int i = 0; i < alternatives.size(); i++) {
            claus.add(new Clau(alternatives.get(i), pesos.get(i)));
        }
        return new ClausCorreccio(claus);
    }

    /** Proposa la nota: suma el pes dels conceptes presents i penalitza els absents. */
    public Proposta avalua(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            return Proposta.of(BigDecimal.ZERO, "Sense resposta");
        }
        String text = " " + normalitza(resposta) + " ";
        BigDecimal score = BigDecimal.ZERO;
        List<String> motius = new ArrayList<>();
        for (Clau clau : claus) {
            boolean present = clau.alternatives().stream()
                    .map(ClausCorreccio::normalitza)
                    .filter(a -> !a.isEmpty())
                    .anyMatch(a -> text.contains(" " + a + " "));
            if (present) {
                score = score.add(clau.pes());
            } else {
                motius.add(Proposta.penalitzacio(clau.pes(), "no esmenta «" + clau.nom() + "»"));
            }
        }
        if (motius.isEmpty()) {
            motius.add("✓ Esmenta tots els conceptes clau");
        }
        return new Proposta(score, motius);
    }

    /** Reparteix els punts a parts iguals; l'arrodoniment es compensa a l'últim concepte. */
    private static List<BigDecimal> repartiment(BigDecimal punts, int n) {
        BigDecimal part = punts.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        List<BigDecimal> result = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) result.add(part);
        result.add(punts.subtract(part.multiply(BigDecimal.valueOf(n - 1))));
        return result;
    }

    /** Minúscules, sense accents i amb la puntuació convertida en espais simples. */
    static String normalitza(String s) {
        String senseAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return senseAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip();
    }
}

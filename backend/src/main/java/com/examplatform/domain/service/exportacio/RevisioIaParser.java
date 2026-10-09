package com.examplatform.domain.service.exportacio;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Llegeix la resposta d'una IA a la revisió d'un examen: files «alumne; pregunta; nota; justificació».
 * Accepta CSV (separador {@code ;}, tabulador o coma) o una taula Markdown, dins o fora d'un bloc de
 * codi, i ignora el text que la IA hi afegeixi al voltant. És una funció pura: no sap res de la BD.
 * Què significa cada fila (alumne, pregunta, nota vàlida…) ho decideix el servei.
 */
public final class RevisioIaParser {

    /** Una fila tal com ve al fitxer, sense interpretar. {@code linia} és la 1-based del text original. */
    public record Fila(int linia, String alumne, String pregunta, String nota, String justificacio) {}

    public static final int MAX_CARACTERS = 2_000_000;

    private static final Pattern CONTE_XIFRA = Pattern.compile("\\d");
    private static final Pattern SEPARADOR_TAULA = Pattern.compile("^:?-{2,}:?$");
    private static final char[] DELIMITADORS = {';', '\t', ','};

    private static final Set<String> COL_ALUMNE = Set.of("alumne", "alumna", "alumne/a", "estudiant", "codi");
    private static final Set<String> COL_PREGUNTA = Set.of("pregunta", "p", "preg", "pregunta.");
    private static final Set<String> COL_NOTA = Set.of("nota", "puntuacio", "punts", "qualificacio");
    private static final Set<String> COL_JUSTIFICACIO = Set.of("justificacio", "comentari", "motiu", "observacions", "feedback");

    private RevisioIaParser() {}

    /** Posicions de les columnes dins de cada fila. */
    private record Columnes(int alumne, int pregunta, int nota, int justificacio) {
        static final Columnes PER_DEFECTE = new Columnes(0, 1, 2, 3);
        int maxObligatori() { return Math.max(alumne, Math.max(pregunta, nota)); }
    }

    public static List<Fila> analitza(String text) {
        if (text == null) return List.of();
        if (text.length() > MAX_CARACTERS) {
            throw new IllegalArgumentException("El fitxer és massa gran (màxim " + MAX_CARACTERS / 1_000_000 + " MB de text)");
        }
        String net = text.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n');
        String[] linies = net.split("\n", -1);
        char delim = triaDelimitador(linies);

        List<Fila> files = new ArrayList<>();
        Columnes cols = null;
        for (int i = 0; i < linies.length; i++) {
            String s = linies[i].strip();
            if (s.isEmpty() || s.startsWith("```")) continue;
            List<String> celes = s.startsWith("|") ? celesTaula(s) : celesCsv(s, delim);
            if (celes.size() < 3 || celes.stream().allMatch(c -> SEPARADOR_TAULA.matcher(c).matches() || c.isEmpty())) continue;
            if (esCapcalera(celes)) {
                if (cols == null) cols = columnesDe(celes);
                continue;
            }
            Columnes c = cols != null ? cols : Columnes.PER_DEFECTE;
            if (celes.size() <= c.maxObligatori()) continue;
            String pregunta = celes.get(c.pregunta());
            if (!CONTE_XIFRA.matcher(pregunta).find()) continue;   // text de la IA, no una fila de dades
            String just = c.justificacio() >= 0 && c.justificacio() < celes.size() ? celes.get(c.justificacio()) : "";
            files.add(new Fila(i + 1, celes.get(c.alumne()), pregunta, celes.get(c.nota()), just));
        }
        return files;
    }

    // ── Separador ─────────────────────────────────────────────────────────────

    /** El separador amb què més files «semblen» dades (pregunta amb xifra i nota amb xifra). */
    private static char triaDelimitador(String[] linies) {
        char millor = ';';
        int max = -1;
        for (char d : DELIMITADORS) {
            int punts = 0;
            for (String l : linies) {
                String s = l.strip();
                if (s.isEmpty() || s.startsWith("|")) continue;
                List<String> c = celesCsv(s, d);
                if (c.size() >= 3 && CONTE_XIFRA.matcher(c.get(1)).find() && CONTE_XIFRA.matcher(c.get(2)).find()) punts++;
            }
            if (punts > max) { max = punts; millor = d; }
        }
        return millor;
    }

    private static List<String> celesCsv(String linia, char delim) {
        CSVFormat fmt = CSVFormat.DEFAULT.builder().setDelimiter(delim).setQuote('"')
                .setIgnoreSurroundingSpaces(true).build();
        try (CSVParser p = CSVParser.parse(linia, fmt)) {
            Iterator<CSVRecord> it = p.iterator();
            if (!it.hasNext()) return List.of();
            List<String> celes = new ArrayList<>();
            it.next().forEach(c -> celes.add(c.strip()));
            return celes;
        } catch (IOException | RuntimeException e) {
            // cometes mal tancades, etc.: es talla sense interpretar-les
            List<String> celes = new ArrayList<>();
            for (String c : linia.split(java.util.regex.Pattern.quote(String.valueOf(delim)), -1)) celes.add(c.strip());
            return celes;
        }
    }

    private static List<String> celesTaula(String linia) {
        String s = linia.strip();
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|") && !s.endsWith("\\|")) s = s.substring(0, s.length() - 1);
        List<String> celes = new ArrayList<>();
        for (String c : s.split("(?<!\\\\)\\|", -1)) celes.add(c.replace("\\|", "|").strip());
        return celes;
    }

    // ── Capçalera ─────────────────────────────────────────────────────────────

    private static String normalitza(String t) {
        String n = Normalizer.normalize(t == null ? "" : t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).strip();
    }

    private static boolean esCapcalera(List<String> celes) {
        boolean alumne = false, nota = false;
        for (String c : celes) {
            String n = normalitza(c);
            if (COL_ALUMNE.contains(n)) alumne = true;
            if (COL_NOTA.contains(n)) nota = true;
        }
        return alumne && nota;
    }

    private static Columnes columnesDe(List<String> celes) {
        int alumne = -1, pregunta = -1, nota = -1, just = -1;
        for (int i = 0; i < celes.size(); i++) {
            String n = normalitza(celes.get(i));
            if (alumne < 0 && COL_ALUMNE.contains(n)) alumne = i;
            else if (pregunta < 0 && COL_PREGUNTA.contains(n)) pregunta = i;
            else if (nota < 0 && COL_NOTA.contains(n)) nota = i;
            else if (just < 0 && COL_JUSTIFICACIO.contains(n)) just = i;
        }
        if (alumne < 0 || pregunta < 0 || nota < 0) return Columnes.PER_DEFECTE;
        return new Columnes(alumne, pregunta, nota, just);
    }
}

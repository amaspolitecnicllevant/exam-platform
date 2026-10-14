package com.examplatform.domain.service;

import com.examplatform.dto.ProgramacioDto;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Llegeix la programació d'un mòdul d'un CSV amb una fila per parella (unitat, competència):
 * {@code unitat;competència;descripció de la competència;nom de la unitat}. Les dues últimes columnes són opcionals.
 * El separador (; , o tabulador) es detecta, i la fila de capçalera és opcional.
 */
public final class ProgramacioCsvParser {

    private ProgramacioCsvParser() {}

    public static ProgramacioDto.Peticio llegeix(String csv) {
        String text = csv == null ? "" : csv.replace("﻿", "");
        if (text.isBlank()) throw new IllegalArgumentException("El CSV és buit");
        char sep = separador(text);
        Map<String, String> descripcions = new LinkedHashMap<>();     // codi CO → descripció
        Map<String, String> noms = new LinkedHashMap<>();             // codi UT → nom
        Map<String, List<String>> perUnitat = new LinkedHashMap<>();  // codi UT → codis CO
        try (CSVParser p = CSVFormat.DEFAULT.builder().setDelimiter(sep).setIgnoreEmptyLines(true).setTrim(true).build()
                .parse(new StringReader(text))) {
            boolean primera = true;
            for (CSVRecord r : p) {
                if (primera) {
                    primera = false;
                    if (esCapcalera(r)) continue;
                }
                long fila = r.getRecordNumber();
                if (r.size() < 2 || r.get(0).isBlank() || r.get(1).isBlank()) {
                    throw new IllegalArgumentException("Fila " + fila + ": calen almenys la unitat i la competència");
                }
                String ut = codi(r.get(0));
                String co = codi(r.get(1));
                String descripcio = r.size() > 2 ? r.get(2) : "";
                String nom = r.size() > 3 ? r.get(3) : "";
                if (!descripcio.isBlank()) {
                    String anterior = descripcions.get(co);
                    if (anterior != null && !anterior.isBlank() && !anterior.equals(descripcio)) {
                        throw new IllegalArgumentException("Fila " + fila + ": la competència " + co
                                + " té dues descripcions diferents");
                    }
                    descripcions.put(co, descripcio);
                } else {
                    descripcions.putIfAbsent(co, "");
                }
                if (!nom.isBlank()) noms.put(ut, nom);
                List<String> cos = perUnitat.computeIfAbsent(ut, k -> new ArrayList<>());
                if (!cos.contains(co)) cos.add(co);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No s'ha pogut llegir el CSV");
        }
        if (perUnitat.isEmpty()) throw new IllegalArgumentException("El CSV no té cap fila");
        List<ProgramacioDto.CompetenciaPeticio> competencies = descripcions.entrySet().stream()
                .map(e -> new ProgramacioDto.CompetenciaPeticio(e.getKey(), e.getValue())).toList();
        List<ProgramacioDto.UnitatPeticio> unitats = perUnitat.entrySet().stream()
                .map(e -> new ProgramacioDto.UnitatPeticio(e.getKey(), noms.getOrDefault(e.getKey(), ""), e.getValue())).toList();
        return new ProgramacioDto.Peticio(competencies, unitats);
    }

    private static String codi(String brut) {
        return brut.strip().toUpperCase(Locale.ROOT);
    }

    private static boolean esCapcalera(CSVRecord r) {
        String a = r.get(0).strip().toLowerCase(Locale.ROOT);
        return a.equals("ut") || a.equals("unitat") || a.equals("unitat de treball");
    }

    /** El separador més freqüent a la primera línia (; , o tabulador); per defecte «;». */
    static char separador(String text) {
        String linia = text.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        long punts = linia.chars().filter(c -> c == ';').count();
        long comes = linia.chars().filter(c -> c == ',').count();
        long tabs = linia.chars().filter(c -> c == '\t').count();
        if (tabs > punts && tabs > comes) return '\t';
        return comes > punts ? ',' : ';';
    }
}

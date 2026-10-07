package com.examplatform.domain.service.emmagatzematge;

import com.examplatform.dto.EmmagatzematgeDto;

import java.util.*;

/** Suma l'espai dels exàmens segons un criteri (professor, departament, cicle, mòdul o examen). */
public final class AgregadorEspai {

    /** Nom de la fila dels exàmens que encara no tenen mòdul (i per tant ni cicle ni departament). */
    public static final String SENSE_MODUL = "(sense mòdul)";

    private AgregadorEspai() {}

    public static EmmagatzematgeDto.Taula agrupa(List<UsExamen> exàmens, Agrupacio agrupacio) {
        Map<String, Acumulat> grups = new LinkedHashMap<>();
        for (UsExamen u : exàmens) {
            String clau = clau(u, agrupacio);
            grups.computeIfAbsent(clau, k -> new Acumulat(nom(u, agrupacio), detall(u, agrupacio))).suma(u);
        }
        List<EmmagatzematgeDto.Fila> files = grups.entrySet().stream()
                .map(e -> e.getValue().fila(e.getKey()))
                .sorted(Comparator.comparingLong(EmmagatzematgeDto.Fila::total).reversed()
                        .thenComparing(EmmagatzematgeDto.Fila::nom, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Acumulat total = new Acumulat("Total", null);
        exàmens.forEach(total::suma);
        return new EmmagatzematgeDto.Taula(agrupacio.name().toLowerCase(Locale.ROOT), files, total.fila("total"));
    }

    private static String clau(UsExamen u, Agrupacio a) {
        return switch (a) {
            case PROFESSOR -> String.valueOf(u.professorId());
            case DEPARTAMENT -> String.valueOf(u.departamentId());
            case CICLE -> String.valueOf(u.cicleId());
            case MODUL -> String.valueOf(u.modulId());
            case EXAMEN -> String.valueOf(u.examId());
        };
    }

    private static String nom(UsExamen u, Agrupacio a) {
        return switch (a) {
            case PROFESSOR -> u.professor() == null || u.professor().isBlank() ? "(sense nom)" : u.professor();
            case DEPARTAMENT -> u.departament() != null ? u.departament() : SENSE_MODUL;
            case CICLE -> u.cicle() != null ? u.cicle() : SENSE_MODUL;
            case MODUL -> u.modul() != null ? u.modul() : SENSE_MODUL;
            case EXAMEN -> u.examTitol();
        };
    }

    /** Segon text de la fila: a «examen» el professor; a «mòdul» el cicle. */
    private static String detall(UsExamen u, Agrupacio a) {
        return switch (a) {
            case EXAMEN -> u.professor();
            case MODUL -> u.cicle();
            case CICLE -> u.departament();
            default -> null;
        };
    }

    private static final class Acumulat {
        final String nom;
        final String detall;
        int examens, fitxersPregunta, lliuraments;
        long midaPregunta, midaLliuraments;

        Acumulat(String nom, String detall) {
            this.nom = nom;
            this.detall = detall;
        }

        void suma(UsExamen u) {
            examens++;
            fitxersPregunta += u.fitxersPregunta();
            midaPregunta += u.midaPregunta();
            lliuraments += u.lliuraments();
            midaLliuraments += u.midaLliuraments();
        }

        EmmagatzematgeDto.Fila fila(String clau) {
            return new EmmagatzematgeDto.Fila(clau, nom, detall, examens, fitxersPregunta, midaPregunta,
                    lliuraments, midaLliuraments, midaPregunta + midaLliuraments);
        }
    }
}

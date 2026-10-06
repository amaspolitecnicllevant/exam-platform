package com.examplatform.domain.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Formats de fitxer que un alumne pot pujar a una pregunta de lliurament.
 *
 * <p>És una llista tancada: no s'admeten executables ni documents amb macros (docm, xlsm…). L'extensió
 * no es fia sola: per als formats amb signatura coneguda es comprova que el contingut hi correspongui.
 * Els de Packet Tracer (pkt, pka, pkz) no tenen una signatura fiable, així que només se'n comprova
 * l'extensió (rebutjar-ne de vàlids seria pitjor).
 */
public final class FormatsFitxer {

    /** Signatura (primers bytes) que ha de tenir el contingut d'un format. */
    public enum Signatura { ZIP, PDF, PNG, JPEG, CAP }

    private static final Map<String, Signatura> PERMESOS = new LinkedHashMap<>();

    static {
        // Ofimàtica (Office i OpenDocument són arxius ZIP)
        PERMESOS.put("docx", Signatura.ZIP);
        PERMESOS.put("xlsx", Signatura.ZIP);
        PERMESOS.put("pptx", Signatura.ZIP);
        PERMESOS.put("odt", Signatura.ZIP);
        PERMESOS.put("ods", Signatura.ZIP);
        PERMESOS.put("odp", Signatura.ZIP);
        PERMESOS.put("pdf", Signatura.PDF);
        // Packet Tracer
        PERMESOS.put("pkt", Signatura.CAP);
        PERMESOS.put("pka", Signatura.CAP);
        PERMESOS.put("pkz", Signatura.CAP);
        // Altres
        PERMESOS.put("zip", Signatura.ZIP);
        PERMESOS.put("png", Signatura.PNG);
        PERMESOS.put("jpg", Signatura.JPEG);
        PERMESOS.put("jpeg", Signatura.JPEG);
        PERMESOS.put("txt", Signatura.CAP);
    }

    /** Bytes del principi del fitxer que cal llegir per comprovar la signatura. */
    public static final int BYTES_CAPCALERA = 1024;

    private FormatsFitxer() {}

    /** Totes les extensions que el sistema admet (en minúscules, sense punt). */
    public static List<String> permesos() {
        return List.copyOf(PERMESOS.keySet());
    }

    /** Extensió en minúscules sense punt, o buida si no en té. */
    public static String extensio(String nomFitxer) {
        if (nomFitxer == null) return "";
        int punt = nomFitxer.lastIndexOf('.');
        if (punt < 0 || punt == nomFitxer.length() - 1) return "";
        return nomFitxer.substring(punt + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Converteix la llista escrita pel professor ({@code docx, XLSX,pkt}) en extensions normalitzades,
     * sense duplicats i en l'ordre donat. Llança IllegalArgumentException si en falta o n'hi ha una
     * que el sistema no admet.
     */
    public static List<String> normalitzaLlista(String text) {
        List<String> res = new ArrayList<>();
        if (text != null) {
            for (String part : text.split(",")) {
                String ext = part.strip().toLowerCase(Locale.ROOT);
                if (ext.startsWith(".")) ext = ext.substring(1);
                if (ext.isEmpty()) continue;
                if (!PERMESOS.containsKey(ext)) {
                    throw new IllegalArgumentException("el format «" + ext + "» no és admès. Formats admesos: "
                            + String.join(", ", permesos()));
                }
                if (!res.contains(ext)) res.add(ext);
            }
        }
        if (res.isEmpty()) {
            throw new IllegalArgumentException("cal indicar almenys un format. Formats admesos: "
                    + String.join(", ", permesos()));
        }
        return res;
    }

    /** Llista desada a la pregunta (docx,xlsx) → extensions; null o buida = tots els formats permesos. */
    public static List<String> deLaPregunta(String desat) {
        if (desat == null || desat.isBlank()) return permesos();
        return List.of(desat.split(","));
    }

    public static boolean esPermes(String ext) {
        return PERMESOS.containsKey(ext);
    }

    /** El contingut (primers bytes) correspon a la signatura que cal per a aquesta extensió? */
    public static boolean signaturaValida(String ext, byte[] cap, int llargada) {
        Signatura s = PERMESOS.get(ext);
        if (s == null) return false;
        return switch (s) {
            case CAP -> true;
            case ZIP -> llargada >= 4 && cap[0] == 'P' && cap[1] == 'K' && cap[2] == 3 && cap[3] == 4;
            case PNG -> llargada >= 8 && (cap[0] & 0xFF) == 0x89 && cap[1] == 'P' && cap[2] == 'N' && cap[3] == 'G';
            case JPEG -> llargada >= 3 && (cap[0] & 0xFF) == 0xFF && (cap[1] & 0xFF) == 0xD8 && (cap[2] & 0xFF) == 0xFF;
            case PDF -> {
                // Alguns PDF tenen text abans de "%PDF-": s'admet dins del primer kilobyte
                String inici = new String(cap, 0, Math.min(llargada, BYTES_CAPCALERA),
                        java.nio.charset.StandardCharsets.ISO_8859_1);
                yield inici.contains("%PDF-");
            }
        };
    }
}

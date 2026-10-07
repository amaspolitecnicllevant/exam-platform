package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.Answer;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZIP amb els fitxers que els alumnes han lliurat a les preguntes de fitxer, un directori per alumne.
 * El pla (què hi va i què falta) es calcula mentre hi ha la transacció; el ZIP s'escriu després, en
 * flux, perquè pot ocupar centenars de MB i no s'ha de carregar sencer a memòria.
 */
public final class ExportacioFitxers {

    /** Un fitxer del ZIP: el nom a dins i on és al disc. */
    public record Entrada(String nomZip, Path ruta) {}

    /** @param absents lliuraments registrats a la BD el fitxer dels quals ja no és al disc */
    public record Pla(List<Entrada> entrades, List<String> absents, String titol, LocalDate data) {}

    private ExportacioFitxers() {}

    public static Pla pla(DadesExamen d, LocalDate data) {
        List<Entrada> entrades = new ArrayList<>();
        List<String> absents = new ArrayList<>();
        Set<String> usats = new HashSet<>();
        for (DadesExamen.Alumne a : d.alumnes()) {
            String local = a.email().contains("@") ? a.email().substring(0, a.email().indexOf('@')) : a.email();
            String directori = DadesExamen.nomNet(a.nom() + " (" + local + ")", 70);
            for (Question q : d.preguntes()) {
                if (q.getTipus() != QuestionType.FILE_UPLOAD) continue;
                Answer r = a.respostes().get(q.getId());
                if (r == null || !r.teFitxer()) continue;
                Path ruta = Path.of(r.getFitxerRuta());
                String descripcio = a.nom() + " · P" + q.getOrdre() + " · " + r.getFitxerNom();
                if (!Files.isReadable(ruta)) {
                    absents.add(descripcio);
                    continue;
                }
                String nom = unic(usats, directori + "/P" + q.getOrdre() + "-" + DadesExamen.nomNet(r.getFitxerNom(), 100));
                entrades.add(new Entrada(nom, ruta));
            }
        }
        return new Pla(entrades, absents, d.exam().getTitle(), data);
    }

    /** Escriu el ZIP: primer un LLEGEIX-ME amb el resum i després els fitxers. */
    public static void escriu(OutputStream out, Pla pla) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("LLEGEIX-ME.txt"));
            zip.write(llegeixMe(pla).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Entrada e : pla.entrades()) {
                zip.putNextEntry(new ZipEntry(e.nomZip()));
                try {
                    Files.copy(e.ruta(), zip);
                } catch (IOException ex) {
                    // Si un fitxer desapareix just ara, el ZIP continua amb la resta
                }
                zip.closeEntry();
            }
        }
    }

    static String llegeixMe(Pla pla) {
        StringBuilder sb = new StringBuilder();
        sb.append("Lliuraments de fitxers de l'examen «").append(pla.titol().replaceAll("[\\r\\n]+", " ")).append("»\n");
        sb.append("Exportat el ").append(pla.data()).append("\n\n");
        sb.append("Fitxers inclosos: ").append(pla.entrades().size()).append("\n");
        sb.append("Estructura: un directori per alumne, amb el fitxer de cada pregunta com a P<número>-<nom original>.\n");
        if (pla.entrades().isEmpty()) sb.append("\nCap alumne ha lliurat cap fitxer.\n");
        if (!pla.absents().isEmpty()) {
            sb.append("\nAVÍS: aquests lliuraments consten a la plataforma però el fitxer ja no és al servidor:\n");
            pla.absents().forEach(a -> sb.append(" - ").append(a.replaceAll("[\\r\\n]+", " ")).append("\n"));
        }
        return sb.toString();
    }

    private static String unic(Set<String> usats, String nom) {
        if (usats.add(nom.toLowerCase(Locale.ROOT))) return nom;
        int punt = nom.lastIndexOf('.');
        String base = punt > nom.lastIndexOf('/') ? nom.substring(0, punt) : nom;
        String ext = punt > nom.lastIndexOf('/') ? nom.substring(punt) : "";
        for (int n = 2; ; n++) {
            String candidat = base + "-" + n + ext;
            if (usats.add(candidat.toLowerCase(Locale.ROOT))) return candidat;
        }
    }
}

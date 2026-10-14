package com.examplatform.domain.service;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionFile;
import com.examplatform.domain.port.DescarregadorImatges;
import com.examplatform.infrastructure.persistence.QuestionFileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * En importar un examen des de Markdown, baixa les imatges {@code ![text](https://…)} (o {@code data:image/…;base64,…})
 * dels enunciats i les incorpora com les que s'afegeixen des de l'editor: un {@link QuestionFile} per pregunta, i
 * l'enunciat passa a referenciar-les amb {@code ![text](fitxer:<id>)}. Si alguna no es pot incorporar es rebutja
 * tota la importació (dins la transacció de l'importador) i s'esborren els fitxers que ja s'havien escrit.
 */
@Service
@RequiredArgsConstructor
public class ImportadorImatgesService {

    private static final Logger log = LoggerFactory.getLogger(ImportadorImatgesService.class);

    /** Un tros de codi (que es deixa tal qual) o una imatge Markdown. */
    private static final Pattern IMATGE = Pattern.compile(
            "(```[\\s\\S]*?```|`[^`\\n]*`)"
                    + "|!\\[([^\\]]*)\\]\\(\\s*<?([^)\\s>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)");
    private static final Pattern DATA_URI = Pattern.compile(
            "data:image/(png|jpeg|jpg|gif|webp);base64,([A-Za-z0-9+/=\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> TIPUS = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "gif", "image/gif", "webp", "image/webp");

    private final DescarregadorImatges descarregador;
    private final QuestionFileRepository fileRepository;

    @Value("${execution.files-host-path}")
    private String filesHostPath;

    /** Mida màxima d'una imatge d'enunciat (bytes), la mateixa que a l'editor. */
    @Value("${exam.imatge-max-bytes:5242880}")
    private long imatgeMaxBytes;

    /** Màxim d'imatges per importació, perquè una sola petició no baixi un nombre il·limitat de fitxers. */
    @Value("${exam.importacio-imatges.maxim:60}")
    private int maximImatges;

    /** Una imatge trobada a un enunciat. */
    record Trobada(String text, String url) {}

    /** Incorpora les imatges de totes les preguntes (que ja han d'estar desades, amb id). */
    public void incorpora(Exam exam) {
        List<Path> escrits = new ArrayList<>();
        Map<String, byte[]> baixades = new HashMap<>();
        int[] total = {0};
        try {
            for (Question q : exam.getQuestions()) {
                String nou = incorporaPregunta(q, baixades, escrits, total);
                if (!nou.equals(q.getEnunciat())) q.setEnunciat(nou);
            }
        } catch (RuntimeException e) {
            escrits.forEach(this::esborra);
            throw e;
        }
    }

    private String incorporaPregunta(Question q, Map<String, byte[]> baixades, List<Path> escrits, int[] total) {
        String enunciat = q.getEnunciat();
        if (enunciat == null || !enunciat.contains("![")) return enunciat;
        Map<String, UUID> fitxersDeLaPregunta = new HashMap<>();   // la mateixa imatge dues vegades = un sol fitxer
        Matcher m = IMATGE.matcher(enunciat);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (m.group(1) != null) {   // codi: no es toca
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            String text = m.group(2);
            String url = m.group(3);
            if (url.startsWith("fitxer:")) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            UUID id = fitxersDeLaPregunta.get(url);
            if (id == null) {
                if (++total[0] > maximImatges) {
                    throw new IllegalArgumentException("L'examen té més de " + maximImatges + " imatges per incorporar");
                }
                try {
                    id = desa(q, url, baixades, escrits);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Pregunta " + Numeracio.numero(q) + ": no s'ha pogut incorporar la imatge «"
                            + curta(url) + "»: " + e.getMessage());
                }
                fitxersDeLaPregunta.put(url, id);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement("![" + text + "](fitxer:" + id + ")"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private UUID desa(Question q, String url, Map<String, byte[]> baixades, List<Path> escrits) {
        byte[] dades = baixades.get(url);
        if (dades == null) {
            dades = obte(url);
            baixades.put(url, dades);
        }
        String ext = extensioPerSignatura(dades);
        if (ext == null) throw new IllegalArgumentException("no és una imatge PNG, JPG, GIF o WebP");
        try {
            Path dir = Path.of(filesHostPath, "questions", q.getId().toString());
            Files.createDirectories(dir);
            Path dest = dir.resolve("img-" + UUID.randomUUID().toString().substring(0, 8) + "." + ext);
            Files.write(dest, dades);
            escrits.add(dest);
            QuestionFile qf = fileRepository.save(QuestionFile.builder()
                    .question(q).filename(nomDe(url, ext)).storedPath(dest.toString())
                    .contentType(TIPUS.get(ext)).fileSize(dades.length).build());
            return qf.getId();
        } catch (IOException e) {
            throw new IllegalStateException("No s'ha pogut desar la imatge al disc", e);
        }
    }

    private byte[] obte(String url) {
        Matcher d = DATA_URI.matcher(url);
        if (url.regionMatches(true, 0, "data:", 0, 5)) {
            if (!d.matches()) throw new IllegalArgumentException("només s'admeten dades en base64 d'una imatge PNG, JPG, GIF o WebP");
            String b64 = d.group(2).replaceAll("\\s", "");
            if (b64.length() / 4 * 3L > imatgeMaxBytes) {
                throw new IllegalArgumentException("pesa més de " + (imatgeMaxBytes / 1024 / 1024) + " MB");
            }
            try {
                return Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("les dades en base64 no són vàlides");
            }
        }
        if (!url.regionMatches(true, 0, "http://", 0, 7) && !url.regionMatches(true, 0, "https://", 0, 8)) {
            throw new IllegalArgumentException("només es poden incorporar imatges d'una adreça http(s) o dades en base64; "
                    + "una ruta local no es pot pujar amb el fitxer .md: afegeix la imatge des de l'editor");
        }
        return descarregador.descarrega(url, imatgeMaxBytes);
    }

    /** Extensió segons els primers bytes: ni la URL ni la capçalera del servidor demostren què és. */
    static String extensioPerSignatura(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "gif";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }

    private static String nomDe(String url, String ext) {
        String nom = "imatge." + ext;
        if (url.startsWith("http")) {
            String camí = url.replaceAll("[?#].*$", "");
            String ultim = camí.substring(camí.lastIndexOf('/') + 1);
            if (!ultim.isBlank() && ultim.contains(".")) nom = ultim;
        }
        nom = nom.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return nom.length() > 100 ? nom.substring(nom.length() - 100) : nom;
    }

    private static String curta(String url) {
        String u = url.startsWith("data:") ? "dades en base64" : url;
        return u.length() > 80 ? u.substring(0, 77) + "…" : u;
    }

    private void esborra(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("No s'ha pogut esborrar {}", p, e);
        }
    }
}

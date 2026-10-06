package com.examplatform.infrastructure.storage;

import com.examplatform.domain.service.FormatsFitxer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Fitxers que els alumnes pugen com a resposta (Word, Excel, Packet Tracer…). Es desen sota
 * {@code <files-host-path>/answers/<sessió>/}, amb un nom que genera el sistema: el nom que envia
 * l'alumne no arriba mai a formar part de la ruta del disc.
 */
@Component
public class FitxersRespostaStorage {

    private static final Logger log = LoggerFactory.getLogger(FitxersRespostaStorage.class);

    /** Fitxer desat: on és, quant ocupa, la seva empremta i els primers bytes (per comprovar el format). */
    public record Desat(Path ruta, long mida, String sha256, byte[] capcalera, int capcaleraLlargada) {}

    /** El fitxer supera la mida màxima: no es desa. */
    public static class MassaGran extends RuntimeException {
        public MassaGran(long maxim) {
            super("El fitxer supera la mida màxima de " + (maxim / (1024 * 1024)) + " MB");
        }
    }

    private final Path arrel;

    public FitxersRespostaStorage(@Value("${execution.files-host-path}") String filesHostPath) {
        this.arrel = Path.of(filesHostPath, "answers").toAbsolutePath().normalize();
    }

    public Path dirSessio(UUID sessionId) {
        return arrel.resolve(sessionId.toString());
    }

    /**
     * Desa el contingut en un fitxer nou de la sessió. Si supera {@code midaMaxima} o hi ha un error
     * d'E/S, no en queda res al disc.
     */
    public Desat desa(UUID sessionId, UUID questionId, String extensio, InputStream in, long midaMaxima)
            throws IOException {
        Path dir = dirSessio(sessionId);
        Files.createDirectories(dir);
        Path dest = dir.resolve(questionId + "-" + UUID.randomUUID() + "." + extensio).normalize();
        if (!dest.startsWith(dir)) throw new IOException("Ruta no vàlida");

        MessageDigest digest = nouDigest();
        byte[] cap = new byte[FormatsFitxer.BYTES_CAPCALERA];
        int capLlargada = 0;
        long total = 0;
        try (InputStream is = in; OutputStream os = Files.newOutputStream(dest, StandardOpenOption.CREATE_NEW)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                total += n;
                if (total > midaMaxima) throw new MassaGran(midaMaxima);
                digest.update(buf, 0, n);
                os.write(buf, 0, n);
                if (capLlargada < cap.length) {
                    int c = Math.min(n, cap.length - capLlargada);
                    System.arraycopy(buf, 0, cap, capLlargada, c);
                    capLlargada += c;
                }
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(dest);
            throw e;
        }
        return new Desat(dest, total, HexFormat.of().formatHex(digest.digest()), cap, capLlargada);
    }

    /** Esborra un fitxer, però només si és dins la carpeta de respostes (mai una ruta arbitrària). */
    public void esborra(String ruta) {
        if (ruta == null) return;
        Path p = Path.of(ruta).toAbsolutePath().normalize();
        if (!p.startsWith(arrel)) {
            log.warn("No s'esborra {}: és fora de {}", p, arrel);
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("No s'ha pogut esborrar {}: {}", p, e.getMessage());
        }
    }

    /** Esborra tots els fitxers d'una sessió (en reiniciar-la o en esborrar l'examen). */
    public void esborraSessio(UUID sessionId) {
        Path dir = dirSessio(sessionId);
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> fitxers = Files.walk(dir)) {
            fitxers.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("No s'ha pogut esborrar {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("No s'ha pogut netejar {}: {}", dir, e.getMessage());
        }
    }

    private static MessageDigest nouDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

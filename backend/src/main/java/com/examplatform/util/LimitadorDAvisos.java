package com.examplatform.util;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deixa passar un avís per clau cada cert temps. Serveix perquè un atac (milers de peticions iguals) no ompli l'auditoria:
 * es desa el primer esdeveniment i els següents, durant la finestra, s'ignoren.
 */
public final class LimitadorDAvisos {

    private static final int MAX_CLAUS = 20_000;

    private final ConcurrentHashMap<String, Long> ultima = new ConcurrentHashMap<>();
    private final long finestraMs;

    public LimitadorDAvisos(Duration finestra) {
        this.finestraMs = finestra.toMillis();
    }

    public boolean permet(String clau) {
        return permet(clau, System.currentTimeMillis());
    }

    boolean permet(String clau, long araMs) {
        if (ultima.size() > MAX_CLAUS) ultima.clear();   // evita créixer sense límit amb claus inventades
        boolean[] passa = {false};
        ultima.compute(clau, (k, anterior) -> {
            if (anterior == null || araMs - anterior >= finestraMs) {
                passa[0] = true;
                return araMs;
            }
            return anterior;
        });
        return passa[0];
    }

    /** Text segur per desar a l'auditoria: sense caràcters de control i amb llargada limitada. */
    public static String net(String text, int max) {
        if (text == null) return "";
        String t = text.strip().replaceAll("\\p{Cntrl}", "");
        return t.length() <= max ? t : t.substring(0, max);
    }
}

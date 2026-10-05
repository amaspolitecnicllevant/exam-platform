package com.examplatform.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Hores per als missatges a l'usuari: les dates internes són UTC i es mostren a la zona del centre. */
public final class HoraLocal {

    private static volatile ZoneId zona = ZoneId.of("Europe/Madrid");

    private HoraLocal() {}

    public static void setZona(ZoneId z) { zona = z; }

    /** Formata una data UTC amb el patró donat, en l'hora local del centre. */
    public static String format(LocalDateTime utc, String patro) {
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(zona).format(DateTimeFormatter.ofPattern(patro));
    }
}

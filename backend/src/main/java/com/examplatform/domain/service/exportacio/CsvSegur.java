package com.examplatform.domain.service.exportacio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ajudes per escriure CSV que s'obren a Excel: neteja de fórmules i format numèric català. */
public final class CsvSegur {

    /** Marca d'ordre de bytes UTF-8: Excel la necessita per detectar els accents. */
    public static final String BOM = "﻿";

    private CsvSegur() {}

    /** Evita la injecció de fórmules (=, +, -, @) en obrir el CSV a Excel. */
    public static String cel(String valor) {
        if (valor == null) return "";
        String v = valor.strip();
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) return "'" + v;
        return v;
    }

    /** Nombre amb coma decimal i dues xifres («7,50»), com espera un Excel en català. Buit si és null. */
    public static String num(BigDecimal valor) {
        return valor == null ? "" : valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** Nombre amb coma decimal i sense zeros sobrers: «2», «2,5», «0,25». Per a punts i percentatges. */
    public static String curt(BigDecimal valor) {
        if (valor == null) return "";
        BigDecimal v = valor.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (v.scale() < 0) v = v.setScale(0);
        return v.toPlainString().replace('.', ',');
    }
}

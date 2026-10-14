package com.examplatform.domain.port;

/** Port per baixar una imatge remota (la implementació concreta és a {@code infrastructure.http}). */
public interface DescarregadorImatges {

    /**
     * Baixa el contingut d'una URL.
     *
     * @param maxBytes mida màxima admesa; si la resposta és més gran es rebutja sense baixar-la sencera
     * @throws IllegalArgumentException amb un motiu comprensible per a l'usuari si no es pot baixar
     */
    byte[] descarrega(String url, long maxBytes);
}

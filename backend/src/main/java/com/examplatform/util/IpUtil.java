package com.examplatform.util;

import jakarta.servlet.http.HttpServletRequest;

public final class IpUtil {

    private IpUtil() {}

    /**
     * IP real del client.
     *
     * <p>No es llegeixen les capçaleres X-Real-IP / X-Forwarded-For directament: qualsevol client
     * les pot enviar. Tomcat (RemoteIpValve, activat amb {@code server.tomcat.remoteip.*}) ja
     * substitueix l'adreça remota pel valor de X-Real-IP només quan la petició arriba des d'un
     * proxy de confiança (nginx), de manera que {@code getRemoteAddr()} és fiable.
     */
    public static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}

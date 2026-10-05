package com.examplatform.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class CidrUtil {

    private CidrUtil() {}

    /**
     * Comprova si la IP del client és dins del rang CIDR indicat.
     * Suporta IPv4 (p. ex. 192.168.10.0/24).
     * Retorna false si la IP o el CIDR no es poden parsejar.
     */
    public static boolean isInCidr(String clientIp, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return false;

            InetAddress networkAddr = InetAddress.getByName(parts[0].trim());
            int prefix = Integer.parseInt(parts[1].trim());

            // IPv4 amb port (p. ex. "10.0.1.1:54321"): exactament un colon → treure el port
            // IPv6 pur (p. ex. "2001:db8::1"): múltiples colons → deixar tal qual
            // IPv6 amb brackets (p. ex. "[::1]:8080"): treure brackets i port
            String cleanIp;
            long colons = clientIp.chars().filter(c -> c == ':').count();
            if (clientIp.startsWith("[")) {
                cleanIp = clientIp.substring(1, clientIp.indexOf(']'));
            } else if (colons == 1) {
                cleanIp = clientIp.split(":")[0];
            } else {
                cleanIp = clientIp;
            }

            InetAddress clientAddr = InetAddress.getByName(cleanIp.trim());

            byte[] net = networkAddr.getAddress();
            byte[] cli = clientAddr.getAddress();

            if (net.length != cli.length) return false; // IPv4 vs IPv6

            int fullBytes     = prefix / 8;
            int remainingBits = prefix % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (net[i] != cli[i]) return false;
            }
            if (remainingBits > 0) {
                int mask = 0xFF & (0xFF << (8 - remainingBits));
                if ((net[fullBytes] & mask) != (cli[fullBytes] & mask)) return false;
            }
            return true;
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }
}

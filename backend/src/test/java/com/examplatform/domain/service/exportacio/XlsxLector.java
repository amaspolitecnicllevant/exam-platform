package com.examplatform.domain.service.exportacio;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Llegeix un .xlsx dels tests: parts del paquet, noms de full i cel·les (text o nombre). */
final class XlsxLector {

    /** Cel·la: tipus ("text" o "nombre"), valor i estil. */
    record Cel_la(String tipus, String valor, int estil) {}

    final Map<String, byte[]> parts = new LinkedHashMap<>();

    XlsxLector(byte[] xlsx) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) parts.put(e.getName(), zip.readAllBytes());
        }
    }

    Document xml(String part) throws Exception {
        var f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(parts.get(part)));
    }

    List<String> nomsDeFull() throws Exception {
        NodeList fulls = xml("xl/workbook.xml").getElementsByTagNameNS("*", "sheet");
        List<String> noms = new ArrayList<>();
        for (int i = 0; i < fulls.getLength(); i++) noms.add(((Element) fulls.item(i)).getAttribute("name"));
        return noms;
    }

    /** Totes les cel·les d'un full per referència («A1», «B2»…). */
    Map<String, Cel_la> cel_les(int full) throws Exception {
        NodeList nodes = xml("xl/worksheets/sheet" + full + ".xml").getElementsByTagNameNS("*", "c");
        Map<String, Cel_la> res = new LinkedHashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element c = (Element) nodes.item(i);
            boolean text = "inlineStr".equals(c.getAttribute("t"));
            String valor = c.getElementsByTagNameNS("*", text ? "t" : "v").item(0).getTextContent();
            res.put(c.getAttribute("r"), new Cel_la(text ? "text" : "nombre", valor, Integer.parseInt(c.getAttribute("s"))));
        }
        return res;
    }

    int files(int full) throws Exception {
        return xml("xl/worksheets/sheet" + full + ".xml").getElementsByTagNameNS("*", "row").getLength();
    }
}

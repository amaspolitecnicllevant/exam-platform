#!/usr/bin/env python3
"""Genera els PDF de les guies (docs/pdf/) a partir dels Markdown de docs/.

Ús (des de qualsevol directori):
    python3 docs/genera-pdfs.py              # totes les guies
    python3 docs/genera-pdfs.py alumne       # només les que continguin «alumne» al nom

Requisits:  pip install markdown weasyprint
Cada vegada que es canviï una guia .md, cal tornar-lo a executar i commitejar els PDF.
"""
import datetime
import pathlib
import re
import subprocess
import sys

import markdown
from weasyprint import HTML

DOCS = pathlib.Path(__file__).resolve().parent
SORTIDA = DOCS / "pdf"

# (fitxer .md, títol al peu de pàgina, versió compacta d'una sola pàgina)
GUIES = [
    ("guia-administrador.md", "Guia de l'administrador", False),
    ("guia-professor.md", "Guia del professor", False),
    ("guia-alumne.md", "Guia de l'alumne", False),
    ("guia-alumne-resum.md", "Resum per a l'alumne", True),
]
NOMS_PDF = {md: md.replace(".md", ".pdf") for md, _, _ in GUIES}

CSS = """
@page {
  size: A4;
  margin: %(marge)s;
  @bottom-left  { content: "SEDEX · %(titol)s · %(data)s"; font: 7.5pt 'Liberation Sans', sans-serif; color: #888; }
  @bottom-right { content: "Pàgina " counter(page) " de " counter(pages); font: 7.5pt 'Liberation Sans', sans-serif; color: #888; }
}
html { font-family: 'Liberation Sans', 'DejaVu Sans', sans-serif; font-size: %(cos)s; line-height: %(interlinia)s; color: #222; }
body { margin: 0; }
h1 { font-size: 21pt; color: #8b1a4a; border-bottom: 2.5px solid #8b1a4a; padding-bottom: 5pt; margin: 0 0 10pt; }
h2 { font-size: 14pt; color: #8b1a4a; border-bottom: 1px solid #ddd; padding-bottom: 2pt; margin: 18pt 0 6pt; break-after: avoid; }
h3 { font-size: 11.5pt; color: #444; margin: 12pt 0 4pt; break-after: avoid; }
p, ul, ol { margin: 4pt 0 6pt; }
li { margin: 1.5pt 0; }
a { color: #8b1a4a; text-decoration: none; }
strong { color: #111; }
hr { border: none; border-top: 1px solid #ddd; margin: %(hr)s 0; }
table { border-collapse: collapse; width: 100%%; font-size: 8.8pt; margin: 6pt 0 8pt; }
th { background: #f1e6ec; text-align: left; }
th, td { border: 1px solid #d3d3d3; padding: 3pt 5pt; vertical-align: top; }
tr { break-inside: avoid; }
td code { overflow-wrap: anywhere; }
td:first-child code { white-space: nowrap; overflow-wrap: normal; }
code { font-family: 'DejaVu Sans Mono', monospace; font-size: 8.4pt; background: #f3f3f3; padding: 0 2pt; border-radius: 2pt; }
pre { background: #f6f6f6; border: 1px solid #dedede; border-radius: 3pt; padding: 6pt 8pt; white-space: pre-wrap;
      overflow-wrap: anywhere; break-inside: avoid; margin: 6pt 0 8pt; }
pre code { background: none; padding: 0; font-size: 8.2pt; }
blockquote { border-left: 3px solid #8b1a4a; margin: 8pt 0; padding: 4pt 10pt; background: #faf5f8; color: #444; }
blockquote p { margin: 2pt 0; }
"""


def arregla_enllacos(text: str) -> str:
    """Als PDF, un enllaç a una altra guia porta al seu PDF; els enllaços a la resta de documents es queden en text."""
    def canvia(m):
        text_enllac, destí = m.group(1), m.group(2)
        nom = pathlib.PurePosixPath(destí).name
        if nom in NOMS_PDF:
            return f"[{text_enllac}]({NOMS_PDF[nom]})"
        return f"`docs/{nom}`" if text_enllac.endswith(".md") else text_enllac
    return re.sub(r"\[([^\]]+)\]\(([^)#\s]+\.md)(?:#[^)]*)?\)", canvia, text)


def genera(fitxer_md: str, titol: str, compacte: bool) -> pathlib.Path:
    text = arregla_enllacos((DOCS / fitxer_md).read_text(encoding="utf-8"))
    cos = markdown.markdown(text, extensions=["tables", "fenced_code", "sane_lists"])
    css = CSS % {
        "titol": titol, "data": datetime.date.today().strftime("%d/%m/%Y"),
        "marge": "15mm 16mm 16mm" if compacte else "17mm 16mm 19mm",
        "cos": "10.3pt" if compacte else "10pt", "interlinia": "1.42" if compacte else "1.45",
        "hr": "6pt" if compacte else "14pt",
    }
    html = f'<!doctype html><html lang="ca"><head><meta charset="utf-8"><title>{titol}</title><style>{css}</style></head><body>{cos}</body></html>'
    SORTIDA.mkdir(exist_ok=True)
    pdf = SORTIDA / NOMS_PDF[fitxer_md]
    HTML(string=html, base_url=str(DOCS)).write_pdf(pdf)
    return pdf


def pagines(pdf: pathlib.Path) -> int:
    sortida = subprocess.run(["pdfinfo", str(pdf)], capture_output=True, text=True).stdout
    m = re.search(r"Pages:\s+(\d+)", sortida)
    return int(m.group(1)) if m else -1


def main() -> int:
    filtre = sys.argv[1].lower() if len(sys.argv) > 1 else ""
    codi = 0
    for fitxer_md, titol, compacte in GUIES:
        if filtre and filtre not in fitxer_md:
            continue
        pdf = genera(fitxer_md, titol, compacte)
        n = pagines(pdf)
        avis = ""
        if compacte and n != 1:
            avis = "  ← AVÍS: el resum hauria de ser d'una sola pàgina"
            codi = 1
        print(f"{pdf.relative_to(DOCS.parent)}: {n} {'pàgina' if n == 1 else 'pàgines'}, {pdf.stat().st_size // 1024} KB{avis}")
    return codi


if __name__ == "__main__":
    sys.exit(main())

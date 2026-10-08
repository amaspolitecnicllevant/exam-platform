#!/usr/bin/env python3
"""Prova de càrrega de la plataforma d'exàmens: simula una classe fent un examen alhora.

Què fa (només per HTTP, com ho faria el navegador):
  1. Prepara: crea un examen de prova (test, text, bash, Java i lliurament de fitxer), importa N alumnes de prova i
     activa l'examen NOMÉS per a ells (els alumnes reals no el veuen mai).
  2. Executa: cada alumne entra, desa mentre «escriu», executa codi, puja un fitxer i entrega cap al final;
     un professor mira el monitor cada 15 s.
  3. Mesura la latència de cada tipus de petició, els errors, i quant triga la correcció en segon pla
     (l'execució del codi en entregar) a acabar.
  4. Escriu un informe (JSON i Markdown) i diu si passa els llindars.

NO neteja: les dades de prova s'esborren amb neteja.sql (vegeu README.md). Corre'l des d'un ordinador de la xarxa,
no des del servidor, perquè el generador no robi CPU a allò que es vol mesurar.

Ús:
    export ADMIN_EMAIL=carrega-admin@prova.invalid ADMIN_PASSWORD=...
    python3 prova_carrega.py --url https://examens.centre.cat:3443 --ca ca.crt --alumnes 120
"""
import argparse
import io
import json
import os
import random
import secrets
import sys
import threading
import time
import zipfile
from datetime import datetime

import requests

PREFIX_EMAIL = "carrega-"
DOMINI = "@prova.invalid"
TITOL_PREFIX = "PROVA DE CÀRREGA"
CODI = {"BASH_CMD": "bash", "BASH_SCRIPT": "bash", "JAVA_PROG": "java"}

# ── Mètriques ────────────────────────────────────────────────────────────────


class Metriques:
    def __init__(self):
        self.t0 = time.time()
        self.mostres = []          # (nom, estat, durada, instant, error)
        self.lock = threading.Lock()

    def afegeix(self, nom, estat, durada, error=None):
        with self.lock:
            self.mostres.append((nom, estat, durada, time.time() - self.t0, error))

    def errors_recents(self, finestra=20.0):
        """(peticions, errors) dels últims `finestra` segons."""
        ara = time.time() - self.t0
        with self.lock:
            recents = [m for m in self.mostres if ara - m[3] <= finestra]
        return len(recents), sum(1 for m in recents if not ok(m[1]))


def ok(estat):
    return 200 <= estat < 300


def percentil(valors, p):
    if not valors:
        return float("nan")
    v = sorted(valors)
    k = (len(v) - 1) * p / 100.0
    a, b = int(k), min(int(k) + 1, len(v) - 1)
    return v[a] + (v[b] - v[a]) * (k - a)


class Client:
    """Un navegador: una sessió HTTP amb el seu token."""

    def __init__(self, base, verify, metriques, timeout=90):
        self.base = base.rstrip("/") + "/api"
        self.m = metriques
        self.timeout = timeout
        self.s = requests.Session()
        self.s.verify = verify
        self.token = None

    def crida(self, nom, metode, ruta, **kw):
        cap = kw.pop("headers", {})
        if self.token:
            cap["Authorization"] = "Bearer " + self.token
        t = time.time()
        try:
            r = self.s.request(metode, self.base + ruta, headers=cap, timeout=self.timeout, **kw)
            self.m.afegeix(nom, r.status_code, time.time() - t)
            return r
        except requests.RequestException as e:
            self.m.afegeix(nom, 0, time.time() - t, type(e).__name__)
            return None

    def login(self, email, contrasenya):
        r = self.crida("POST /auth/login", "POST", "/auth/login", json={"email": email, "password": contrasenya})
        if r is not None and ok(r.status_code):
            self.token = r.json().get("token")
        return self.token is not None


# ── L'examen de prova ────────────────────────────────────────────────────────

TEXTOS_CURTS = ["Assigna adreces IP automàticament als equips de la xarxa.",
                "El servidor DHCP dóna la configuració de xarxa de manera automàtica."]
SCRIPT_BASH = '#!/bin/bash\necho "Hola, $1!"\n'
JAVA = 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("hola");\n    }\n}\n'


def examen_md(titol):
    test = "".join(f"""## {n}. [choice] [pts:1]
Pregunta de test número {n}: quina és l'opció correcta?
- a) Primera
- b) Segona
- c) Tercera
- d) Quarta

:::model
b
:::

""" for n in range(1, 5))
    return f"""# {titol}
durada: 90
---
### Part 1 — Teoria

{test}## 5. [short] [pts:0.5]
Què fa un servidor DHCP?

:::model
Assigna automàticament adreces IP.
:::

:::clau
adreça IP, adreces IP | 0.25
automàtic, automàticament | 0.25
:::

## 6. [short] [pts:0.5]
Explica breument què fa DHCP a la xarxa.

:::model
Assigna automàticament la configuració de xarxa.
:::

:::clau
configuració, xarxa | 0.25
automàtic, automàticament | 0.25
:::

### Part 2 — Pràctica

## 7. [bash-cmd] [pts:1]
Escriu una comanda que mostri els números de l'1 al 5, un per línia.

:::model
seq 1 5
:::

:::output-contains
1
3
5
:::

## 8. [bash-script] [pts:1.5]
Escriu un script que rebi un nom i mostri `Hola, <nom>!`.

:::model
#!/bin/bash
echo "Hola, $1!"
:::

:::test
#!/bin/bash
[ "$(bash "$SCRIPT_FILE" Anna 2>/dev/null)" = "Hola, Anna!" ] && exit 0 || exit 1
:::

## 9. [java-prog] [pts:1.5]
Escriu un programa Java que mostri `hola`.

:::model
public class Main {{ public static void main(String[] a) {{ System.out.println("hola"); }} }}
:::

:::output-contains
hola
:::

## 10. [fitxer] [pts:1] [formats:docx]
Entrega l'informe en Word.
"""


def fitxer_docx(mida_kb=150):
    """Un .docx mínim vàlid per al servidor: un ZIP (signatura PK) amb dades aleatòries."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_STORED) as z:
        z.writestr("word/document.xml", "<w:document/>")
        z.writestr("word/dades.bin", os.urandom(mida_kb * 1024))
    return buf.getvalue()


# ── Preparació ───────────────────────────────────────────────────────────────


def prepara(args, m):
    admin = Client(args.url, args.ca, m)
    if not admin.login(os.environ["ADMIN_EMAIL"], os.environ["ADMIN_PASSWORD"]):
        sys.exit("No s'ha pogut entrar com a administrador de prova (ADMIN_EMAIL / ADMIN_PASSWORD).")

    titol = f"{TITOL_PREFIX} {datetime.now():%Y%m%d-%H%M%S}"
    r = admin.crida("setup: crear examen", "POST", "/exams",
                    files={"file": ("examen.md", examen_md(titol).encode("utf-8"), "text/markdown")})
    if r is None or not ok(r.status_code):
        sys.exit(f"No s'ha pogut crear l'examen de prova: {r.status_code if r is not None else 'sense resposta'} "
                 f"{r.text[:300] if r is not None else ''}")
    examen = r.json()

    emails = [f"{PREFIX_EMAIL}{i:03d}{DOMINI}" for i in range(1, args.alumnes + 1)]
    csv = "nom;email;contrasenya\n" + "".join(
        f"Prova Carrega {i:03d};{e};{args.contrasenya_alumnes}\n" for i, e in enumerate(emails, 1))
    r = admin.crida("setup: importar alumnes", "POST", "/users/import",
                    files={"file": ("alumnes.csv", csv.encode("utf-8"), "text/csv")})
    if r is None or not ok(r.status_code):
        sys.exit(f"No s'han pogut importar els alumnes: {r.status_code if r is not None else 'sense resposta'} "
                 f"{r.text[:300] if r is not None else ''}")

    r = admin.crida("setup: llistar usuaris", "GET", "/users")
    ids = {u["email"]: u["id"] for u in r.json() if u["email"] in set(emails)}
    if len(ids) != len(emails):
        sys.exit(f"Només s'han trobat {len(ids)} dels {len(emails)} alumnes de prova.")

    r = admin.crida("setup: activar examen", "POST", f"/exams/{examen['id']}/publish",
                    json={"alumneIds": list(ids.values())})
    if r is None or not ok(r.status_code):
        sys.exit(f"No s'ha pogut activar l'examen: {r.status_code if r is not None else 'sense resposta'} "
                 f"{r.text[:300] if r is not None else ''}")
    return admin, examen["id"], titol, emails


# ── Un alumne ────────────────────────────────────────────────────────────────


def alumne(i, email, ctx):
    rng = random.Random(ctx.args.seed * 1000 + i)
    c = Client(ctx.args.url, ctx.args.ca, ctx.m)
    res = {"alumne": i, "login": False, "inici": False, "entregat": False, "temps_entrega": None}
    ctx.resultats[i] = res
    if ctx.aturada.wait(rng.uniform(0, ctx.args.arribada)):
        return

    if not c.login(email, ctx.args.contrasenya_alumnes):
        return
    res["login"] = True
    c.crida("GET /exams/published", "GET", "/exams/published")
    r = c.crida("GET /exams/{id}", "GET", f"/exams/{ctx.examen_id}")
    if r is None or not ok(r.status_code):
        return
    preguntes = [q for q in r.json()["questions"] if q["tipus"] != "SECTION"]
    r = c.crida("POST /sessions/start", "POST", f"/sessions/start/{ctx.examen_id}")
    if r is None or not ok(r.status_code):
        return
    sessio = r.json()["id"]
    res["inici"] = True

    resposta_id = {}     # id de pregunta -> id de resposta
    fi_treball = ctx.t_inici + ctx.args.durada - rng.uniform(0, ctx.args.finestra_entrega)

    def queda():
        return time.time() < fi_treball and not ctx.aturada.is_set()

    def pausa(a, b):
        ctx.aturada.wait(rng.uniform(a, b))

    def desa(q, text):
        r = c.crida("PUT /sessions/{id}/answers (desar)", "PUT", f"/sessions/{sessio}/answers",
                    json={"questionId": q["id"], "contingut": text})
        if r is not None and ok(r.status_code):
            resposta_id[q["id"]] = r.json().get("id")

    def executa(q):
        if q["id"] in resposta_id:
            c.crida(f"POST /executions/run ({CODI[q['tipus']]})", "POST", f"/executions/{resposta_id[q['id']]}/run")

    def treballa(q):
        t = q["tipus"]
        if t == "CHOICE":
            desa(q, rng.choice("abcd"))
        elif t in ("SHORT", "LONG", "TEXT"):
            base = rng.choice(TEXTOS_CURTS)
            for k in range(rng.randint(3, 6)):          # el desament és cada ~500 ms mentre escriu
                if not queda():
                    return
                desa(q, base[: max(5, len(base) * (k + 1) // 5)])
                pausa(1.5, 4.0)
        elif t in CODI:
            codi = {"BASH_CMD": "seq 1 5", "BASH_SCRIPT": SCRIPT_BASH, "JAVA_PROG": JAVA}[t]
            for k in range(rng.randint(2, 5)):
                if not queda():
                    return
                desa(q, codi[: max(4, len(codi) * (k + 1) // 4)])
                pausa(1.5, 4.0)
            desa(q, codi)
            for _ in range(rng.randint(1, 3 if t != "JAVA_PROG" else 2)):
                if not queda():
                    return
                executa(q)
                pausa(2.0, 6.0)
        elif t == "FILE_UPLOAD":
            for _ in range(2 if rng.random() < 0.25 else 1):
                if not queda():
                    return
                c.crida("POST /sessions/{id}/questions/{id}/file (pujar)", "POST",
                        f"/sessions/{sessio}/questions/{q['id']}/file",
                        files={"file": ("informe.docx", fitxer_docx(rng.choice([80, 150, 300])), "application/octet-stream")})
                pausa(2.0, 5.0)

    ordre = preguntes[:]
    rng.shuffle(ordre)
    while queda():
        for q in ordre:
            if not queda():
                break
            treballa(q)
            if rng.random() < 0.15:
                c.crida("POST /sessions/{id}/focus-loss", "POST", f"/sessions/{sessio}/focus-loss")
            pausa(1.0, 6.0)

    # Abans d'entregar, tot el que no s'ha respost (per si el temps s'ha acabat a mig bucle)
    for q in preguntes:
        if q["id"] not in resposta_id and q["tipus"] not in ("FILE_UPLOAD",):
            desa(q, "b" if q["tipus"] == "CHOICE" else "resposta")

    ctx.aturada.wait(max(0, fi_treball - time.time()))
    t = time.time()
    r = c.crida("POST /sessions/{id}/submit (entregar)", "POST", f"/sessions/{sessio}/submit")
    if r is not None and ok(r.status_code):
        res["entregat"] = True
        res["temps_entrega"] = t - ctx.m.t0
        ctx.ultima_entrega = max(ctx.ultima_entrega, time.time())
        c.crida("GET /sessions/{id} (resultats)", "GET", f"/sessions/{sessio}")
        c.crida("GET /sessions/my/historial", "GET", "/sessions/my/historial")


# ── Professor i correcció en segon pla ───────────────────────────────────────


def professor(admin, ctx):
    while not ctx.fi_alumnes.wait(15):
        admin.crida("GET /sessions/exam/{id}/monitor", "GET", f"/sessions/exam/{ctx.examen_id}/monitor")


def espera_correccio(admin, ctx, maxim):
    """Espera que totes les respostes de codi entregades tinguin nota proposada; torna (segons, evolució)."""
    r = admin.crida("setup: preguntes", "GET", f"/exams/{ctx.examen_id}")
    codi_ids = {q["id"] for q in r.json()["questions"] if q["tipus"] in CODI}
    inici, evolucio = time.time(), []
    while time.time() - inici < maxim:
        r = admin.crida("GET /sessions/exam/{id} (sondeig correcció)", "GET", f"/sessions/exam/{ctx.examen_id}")
        if r is None or not ok(r.status_code):
            time.sleep(5)
            continue
        entregades = [s for s in r.json() if s.get("status") == "SUBMITTED"]
        pendents = sum(1 for s in entregades for a in s.get("answers", [])
                       if a["questionId"] in codi_ids and a.get("autoScore") is None)
        total = len(entregades) * len(codi_ids)
        evolucio.append((round(time.time() - ctx.ultima_entrega, 1), pendents))
        print(f"\r  correcció en segon pla: {total - pendents}/{total} corregides", end="", flush=True)
        if pendents == 0:
            print()
            return time.time() - ctx.ultima_entrega, evolucio
        time.sleep(5)
    print()
    return None, evolucio


# ── Informe ──────────────────────────────────────────────────────────────────


def informe(args, m, ctx, titol, durada_correccio, evolucio, avortada):
    per_nom = {}
    for nom, estat, durada, _, _ in m.mostres:
        if nom.startswith("setup:"):
            continue
        per_nom.setdefault(nom, []).append((estat, durada))
    files = []
    for nom, v in sorted(per_nom.items()):
        lat = [d for _, d in v]
        files.append({"peticio": nom, "n": len(v), "errors": sum(1 for e, _ in v if not ok(e)),
                      "p50": percentil(lat, 50), "p95": percentil(lat, 95), "p99": percentil(lat, 99), "max": max(lat)})
    totals = [(e, d) for k, v in per_nom.items() for e, d in v]
    errors = sum(1 for e, _ in totals if not ok(e))
    estats = {}
    for nom, estat, _, _, err in m.mostres:
        if not nom.startswith("setup:") and not ok(estat):
            clau = f"{estat or err}"
            estats[clau] = estats.get(clau, 0) + 1

    # Pic de peticions per segon (finestra de 5 s)
    instants = sorted(x[3] for x in m.mostres if not x[0].startswith("setup:"))
    pic, j = 0.0, 0
    for i, t in enumerate(instants):
        while t - instants[j] > 5:
            j += 1
        pic = max(pic, (i - j + 1) / 5.0)

    res = list(ctx.resultats.values())
    n = len(res)
    entregats = sum(1 for r in res if r["entregat"])
    def fila(prefix):
        return next((f for f in files if f["peticio"].startswith(prefix)), None)

    llindars = []
    def comprova(nom, valor, maxim, unitat="s"):
        passa = valor is not None and valor == valor and valor <= maxim
        llindars.append({"criteri": nom, "valor": valor, "llindar": maxim, "unitat": unitat, "passa": bool(passa)})

    d = fila("PUT /sessions/{id}/answers")
    comprova("Desar una resposta (p95)", d["p95"] if d else None, args.llindar_desar)
    comprova("Entregar l'examen (p95)", fila("POST /sessions/{id}/submit")["p95"] if fila("POST /sessions/{id}/submit") else None, args.llindar_entregar)
    for tipus in ("bash", "java"):
        e = fila(f"POST /executions/run ({tipus})")
        comprova(f"Executar codi {tipus} (p95)", e["p95"] if e else None, args.llindar_executar)
    comprova("Percentatge d'errors", 100.0 * errors / max(1, len(totals)), args.llindar_errors, "%")
    llindars.append({"criteri": "Alumnes que acaben l'examen", "valor": 100.0 * entregats / max(1, n), "llindar": 100.0,
                     "unitat": "%", "passa": entregats == n and not avortada, "minim": True})
    comprova("Correcció en segon pla acabada", durada_correccio, args.llindar_correccio)

    return {"titol": titol, "data": datetime.now().isoformat(timespec="seconds"), "alumnes": n, "entregats": entregats,
            "durada_examen_s": args.durada, "arribada_s": args.arribada, "finestra_entrega_s": args.finestra_entrega,
            "avortada": avortada, "peticions": len(totals), "errors": errors, "errors_per_tipus": estats,
            "pic_peticions_per_segon": pic, "durada_correccio_s": durada_correccio, "evolucio_correccio": evolucio,
            "peticions_detall": files, "llindars": llindars}


def text_informe(inf):
    fmt = lambda x: "—" if x is None or x != x else f"{x:.2f}"
    s = [f"# Prova de càrrega — {inf['titol']}", "",
         f"{inf['alumnes']} alumnes simultanis · examen de {inf['durada_examen_s']} s · arribada en {inf['arribada_s']} s · "
         f"entregues en els últims {inf['finestra_entrega_s']} s", "",
         f"- Entregats: **{inf['entregats']} de {inf['alumnes']}**" + (" · **PROVA AVORTADA**" if inf["avortada"] else ""),
         f"- Peticions: {inf['peticions']} · errors: **{inf['errors']}** {inf['errors_per_tipus'] or ''}",
         f"- Pic de peticions: {inf['pic_peticions_per_segon']:.1f} per segon (finestra de 5 s)",
         f"- Correcció en segon pla acabada {fmt(inf['durada_correccio_s'])} s després de l'última entrega", "",
         "## Llindars", "", "| Criteri | Valor | Llindar | |", "|:--|--:|--:|:--:|"]
    for l in inf["llindars"]:
        s.append(f"| {l['criteri']} | {fmt(l['valor'])} {l['unitat']} | {'≥' if l.get('minim') else '≤'} {l['llindar']:.0f} {l['unitat']} | {'✅' if l['passa'] else '❌'} |")
    s += ["", "## Latència per tipus de petició (segons)", "",
          "| Petició | N | Errors | p50 | p95 | p99 | màx |", "|:--|--:|--:|--:|--:|--:|--:|"]
    for f in inf["peticions_detall"]:
        s.append(f"| {f['peticio']} | {f['n']} | {f['errors']} | {fmt(f['p50'])} | {fmt(f['p95'])} | {fmt(f['p99'])} | {fmt(f['max'])} |")
    return "\n".join(s) + "\n"


# ── Principal ────────────────────────────────────────────────────────────────


class Ctx:
    pass


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--url", default="https://localhost:3443")
    p.add_argument("--ca", default=True, help="certificat de la CA (ca.crt); per defecte, els del sistema")
    p.add_argument("--alumnes", type=int, default=120)
    p.add_argument("--durada", type=int, default=360, help="segons de l'examen simulat (no els 90 min reals)")
    p.add_argument("--arribada", type=int, default=30, help="segons en què entren tots els alumnes")
    p.add_argument("--finestra-entrega", type=int, default=45, help="les entregues cauen en els últims N segons")
    p.add_argument("--contrasenya-alumnes", default=None)
    p.add_argument("--sortida", default="resultats")
    p.add_argument("--seed", type=int, default=7)
    p.add_argument("--maxim-correccio", type=int, default=1500)
    p.add_argument("--llindar-desar", type=float, default=1.0)
    p.add_argument("--llindar-entregar", type=float, default=3.0)
    p.add_argument("--llindar-executar", type=float, default=20.0)
    p.add_argument("--llindar-errors", type=float, default=1.0)
    p.add_argument("--llindar-correccio", type=float, default=300.0)
    args = p.parse_args()
    if not (1 <= args.alumnes <= 500):
        sys.exit("--alumnes ha d'estar entre 1 i 500")
    if args.durada < args.arribada + args.finestra_entrega + 10:
        sys.exit("--durada ha de ser més gran que arribada + finestra d'entrega + 10 s")
    for v in ("ADMIN_EMAIL", "ADMIN_PASSWORD"):
        if not os.environ.get(v):
            sys.exit(f"Cal definir {v} (un administrador de prova; vegeu README.md)")
    args.contrasenya_alumnes = args.contrasenya_alumnes or ("Pc-" + secrets.token_hex(8))

    requests.packages.urllib3.disable_warnings()
    m = Metriques()
    print("Preparant l'examen i els alumnes de prova…")
    admin, examen_id, titol, emails = prepara(args, m)
    print(f"  examen «{titol}» creat i activat per a {len(emails)} alumnes de prova")

    ctx = Ctx()
    ctx.args, ctx.m, ctx.examen_id = args, m, examen_id
    ctx.resultats, ctx.aturada, ctx.fi_alumnes = {}, threading.Event(), threading.Event()
    ctx.t_inici = time.time()
    ctx.ultima_entrega = ctx.t_inici
    m.t0 = ctx.t_inici

    fils = [threading.Thread(target=alumne, args=(i, e, ctx), daemon=True) for i, e in enumerate(emails, 1)]
    prof = threading.Thread(target=professor, args=(admin, ctx), daemon=True)
    print(f"Començant: {args.alumnes} alumnes, {args.durada} s…")
    prof.start()
    for f in fils:
        f.start()

    avortada = False
    while any(f.is_alive() for f in fils):
        time.sleep(5)
        n, e = m.errors_recents(20)
        vius = sum(1 for f in fils if f.is_alive())
        print(f"\r  t={time.time() - ctx.t_inici:5.0f}s  alumnes actius={vius:3d}  entregats="
              f"{sum(1 for r in ctx.resultats.values() if r['entregat']):3d}  errors (20 s)={e}/{n}   ", end="", flush=True)
        if n >= 60 and e / n > 0.30:
            avortada = True
            ctx.aturada.set()
            print("\n  ⚠ Més del 30 % d'errors: s'atura la prova.")
            break
        if time.time() - ctx.t_inici > args.durada + 300:
            avortada = True
            ctx.aturada.set()
            print("\n  ⚠ La prova s'ha allargat massa: s'atura.")
            break
    print()
    for f in fils:
        f.join(timeout=60)
    ctx.fi_alumnes.set()
    prof.join(timeout=20)

    durada_correccio, evolucio = None, []
    if not avortada:
        print("Esperant la correcció en segon pla…")
        durada_correccio, evolucio = espera_correccio(admin, ctx, args.maxim_correccio)

    inf = informe(args, m, ctx, titol, durada_correccio, evolucio, avortada)
    os.makedirs(args.sortida, exist_ok=True)
    base = os.path.join(args.sortida, f"prova-{datetime.now():%Y%m%d-%H%M%S}")
    with open(base + ".json", "w", encoding="utf-8") as f:
        json.dump(inf, f, ensure_ascii=False, indent=2)
    with open(base + ".md", "w", encoding="utf-8") as f:
        f.write(text_informe(inf))
    print(text_informe(inf))
    print(f"Informe: {base}.md / .json\nRecorda: neteja les dades de prova (neteja.sql).")
    return 0 if all(l["passa"] for l in inf["llindars"]) else 1


if __name__ == "__main__":
    sys.exit(main())

import ssl, time, collections, concurrent.futures as cf
import urllib.request, urllib.error

ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE


def peticio(base, metode, ruta):
    req = urllib.request.Request(base + ruta, method=metode, data=b"" if metode == "POST" else None)
    try:
        with urllib.request.urlopen(req, timeout=10, context=ctx) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception:
        return 0


def rafega(base, metode, ruta, n, fils=20):
    with cf.ThreadPoolExecutor(fils) as ex:
        return collections.Counter(ex.map(lambda _: peticio(base, metode, ruta), range(n)))


def ritme(base, metode, ruta, per_segon, segons):
    """Peticions a un ritme constant (com un usuari normal, però exagerat)."""
    resultat = collections.Counter()
    fi = time.time() + segons
    while time.time() < fi:
        t0 = time.time()
        resultat[peticio(base, metode, ruta)] += 1
        time.sleep(max(0, 1 / per_segon - (time.time() - t0)))
    return resultat


def mostra(titol, c, espera429):
    n429 = c.get(429, 0)
    ok = (n429 > 0) if espera429 else (n429 == 0)
    print(f"  [{'OK ' if ok else 'FALLA'}] {titol}: {dict(sorted(c.items()))}")
    return ok


tot = []
for nom, base in [("HTTP", "http://127.0.0.1:18081"), ("HTTPS", "https://127.0.0.1:18443")]:
    print(f"== {nom}")
    time.sleep(2)   # deixa buidar els comptadors entre blocs
    tot.append(mostra("ús normal de l'API: 15 peticions/s durant 4 s", ritme(base, "GET", "/api/exams/mine", 15, 4), False))
    tot.append(mostra("càrrega de la pàgina: 40 fitxers estàtics a l'hora", rafega(base, "GET", "/", 40), False))
    time.sleep(2)
    tot.append(mostra("bombardeig a l'API: 1500 peticions de cop", rafega(base, "GET", "/api/exams/mine", 1500, 40), True))
    time.sleep(4)
    tot.append(mostra("inici de sessió: 8 peticions seguides (normal)", rafega(base, "POST", "/api/auth/login", 8, 2), False))
    time.sleep(2)
    tot.append(mostra("inici de sessió: 300 intents de cop", rafega(base, "POST", "/api/auth/login", 300, 30), True))
    time.sleep(2)
    tot.append(mostra("informe d'ordinador: 1 cada pocs segons (normal)", ritme(base, "POST", "/api/equips/informe", 1, 4), False))
    time.sleep(2)
    tot.append(mostra("informe d'ordinador: 100 de cop", rafega(base, "POST", "/api/equips/informe", 100, 20), True))
    time.sleep(2)
    # Un client abusiu no ha d'impedir que la pàgina es serveixi per a la resta del temps
    print(f"  després del bombardeig, la pàgina principal respon: {peticio(base, 'GET', '/')}")

print()
print("RESULTAT:", "tot correcte" if all(tot) else "HI HA FALLADES")

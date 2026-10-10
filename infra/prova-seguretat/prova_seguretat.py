"""Prova de seguretat contra una instància LOCAL temporal (no la de producció)."""
import json, subprocess, urllib.request, urllib.error, urllib.parse, sys

BASE = "http://127.0.0.1:18080"
fallades = []


def crida(metode, ruta, cos=None, token=None, ip=None, form=None, cap=None):
    dades = None
    capcaleres = {}
    if cos is not None:
        dades = json.dumps(cos).encode()
        capcaleres["Content-Type"] = "application/json"
    if form is not None:
        dades = urllib.parse.urlencode(form).encode()
        capcaleres["Content-Type"] = "application/x-www-form-urlencoded"
    if token:
        capcaleres["Authorization"] = "Bearer " + token
    if ip:
        capcaleres["X-Real-IP"] = ip
    if cap:
        capcaleres.update(cap)
    req = urllib.request.Request(BASE + ruta, data=dades, method=metode, headers=capcaleres)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            text = r.read().decode()
            return r.status, text
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def js(text):
    try:
        return json.loads(text)
    except Exception:
        return {}


def comprova(descripcio, condicio, detall=""):
    estat = "OK " if condicio else "FALLA"
    print(f"  [{estat}] {descripcio}" + (f"  ({detall})" if detall and not condicio else ""))
    if not condicio:
        fallades.append(descripcio)


def sql(consulta):
    subprocess.run(["docker", "exec", "pg-prova", "psql", "-U", "examuser", "-d", "examplatform", "-v", "ON_ERROR_STOP=1", "-c", consulta],
                   check=True, capture_output=True)


def sql_valor(consulta):
    r = subprocess.run(["docker", "exec", "pg-prova", "psql", "-U", "examuser", "-d", "examplatform", "-tA", "-c", consulta],
                       check=True, capture_output=True, text=True)
    return r.stdout.strip()


def entra(email, pwd, ip="10.0.0.1"):
    codi, text = crida("POST", "/api/auth/login", {"email": email, "password": pwd}, ip=ip)
    return codi, js(text).get("token")


# ── Usuaris de prova (directament a la BD, amb bcrypt de pgcrypto). La prova es pot repetir: es neteja abans.
import time
sql("CREATE EXTENSION IF NOT EXISTS pgcrypto")
sql("DELETE FROM equips_aula; DELETE FROM aules; DELETE FROM users WHERE email LIKE '%@prova.cat'")
for nom, email, rol, pwd in [("Admin", "admin@prova.cat", "ADMIN", "Contrasenya-admin-1"),
                              ("Prof", "prof@prova.cat", "PROFESSOR", "Contrasenya-prof-12"),
                              ("Alumne", "alumne@prova.cat", "STUDENT", "alumne-1234")]:
    sql(f"INSERT INTO users (name,email,password_hash,role,enabled) VALUES ('{nom}','{email}',crypt('{pwd}',gen_salt('bf',10)),'{rol}',true) ON CONFLICT DO NOTHING")

print("1. Configuració pública / privada")
c, tok_admin = entra("admin@prova.cat", "Contrasenya-admin-1")
comprova("l'administrador entra", c == 200 and tok_admin)
c, tok_prof = entra("prof@prova.cat", "Contrasenya-prof-12")
c2, tok_alumne = entra("alumne@prova.cat", "alumne-1234")
anonim = js(crida("GET", "/api/configuracio")[1])
alumne = js(crida("GET", "/api/configuracio", token=tok_alumne)[1])
prof = js(crida("GET", "/api/configuracio", token=tok_prof)[1])
comprova("anònim: sense llindars", anonim.get("copiesLlindar") == 0 and anonim.get("focusLossThreshold") == 0)
comprova("alumne: sense llindars", alumne.get("copiesLlindar") == 0)
comprova("professor: amb llindars", prof.get("copiesLlindar", 0) > 0 and prof.get("focusLossThreshold", 0) > 0, str(prof))

print("2. Autorització bàsica")
comprova("sense sessió: 403", crida("GET", "/api/users")[0] == 403)
comprova("alumne no veu els usuaris: 403", crida("GET", "/api/users", token=tok_alumne)[0] == 403)
comprova("professor sí que els veu", crida("GET", "/api/users", token=tok_prof)[0] == 200)
comprova("professor no tanca sessions d'altri: 403", crida("POST", "/api/users/00000000-0000-0000-0000-000000000000/revoca-sessions", token=tok_prof)[0] == 403)

print("3. Renovació i revocació de sessions")
time.sleep(1.2)   # el token porta l'hora en segons: un refresh dins del mateix segon dona un token idèntic
c, text = crida("POST", "/api/auth/refresh", token=tok_prof)
nou = js(text).get("token")
comprova("refresh dona un token nou", c == 200 and nou and nou != tok_prof)
comprova("el token renovat funciona", crida("GET", "/api/exams/mine", token=nou)[0] == 200)
id_prof = sql_valor("SELECT id FROM users WHERE email='prof@prova.cat'")
c, _ = crida("POST", f"/api/users/{id_prof}/revoca-sessions", token=tok_admin)
comprova("l'administrador tanca les sessions del professor (204)", c == 204)
comprova("el token antic ja no val", crida("GET", "/api/exams/mine", token=tok_prof)[0] in (401, 403))
comprova("el token renovat tampoc", crida("GET", "/api/exams/mine", token=nou)[0] in (401, 403))
c, tok_prof = entra("prof@prova.cat", "Contrasenya-prof-12", ip="10.0.0.2")
comprova("el professor torna a entrar", c == 200 and crida("GET", "/api/exams/mine", token=tok_prof)[0] == 200)

print("4. Contrasenyes")
c, text = crida("POST", "/api/users/me/password", {"actual": "Contrasenya-prof-12", "nova": "onze-chars1"}, token=tok_prof)
comprova("professor: contrasenya de 11 caràcters rebutjada", c == 400 and "12 i 72" in text, text)
c, text = crida("POST", "/api/users/me/password", {"actual": "Contrasenya-prof-12", "nova": "Nova-contrasenya-9"}, token=tok_prof)
nou_prof = js(text).get("token")
comprova("canvi de contrasenya correcte retorna un token nou", c == 200 and nou_prof, text)
comprova("el token anterior queda tancat", crida("GET", "/api/exams/mine", token=tok_prof)[0] in (401, 403))
comprova("el token nou funciona", crida("GET", "/api/exams/mine", token=nou_prof)[0] == 200)
c, _ = entra("prof@prova.cat", "Contrasenya-prof-12", ip="10.0.0.3")
comprova("la contrasenya antiga ja no entra (401)", c == 401)
c, tok_prof = entra("prof@prova.cat", "Nova-contrasenya-9", ip="10.0.0.3")
comprova("la nova sí", c == 200)

print("5. Un alumne no pot bloquejar el compte d'un professor")
for i in range(5):
    crida("POST", "/api/auth/login", {"email": "prof@prova.cat", "password": "mala"}, ip="10.9.9.9")
c, _ = crida("POST", "/api/auth/login", {"email": "prof@prova.cat", "password": "mala"}, ip="10.9.9.9")
comprova("l'atacant queda bloquejat (429)", c == 429)
c, _ = crida("POST", "/api/auth/login", {"email": "prof@prova.cat", "password": "Nova-contrasenya-9"}, ip="10.9.9.8")
comprova("el professor, des de la seva adreça, hi entra (200)", c == 200)

print("6. Usuari inhabilitat")
sql("UPDATE users SET enabled=false WHERE email='alumne@prova.cat'")
comprova("el token d'un usuari inhabilitat deixa de valer", crida("GET", "/api/exams/published", token=tok_alumne)[0] in (401, 403))
sql("UPDATE users SET enabled=true WHERE email='alumne@prova.cat'")

print("7. Informes d'ordinadors, estat i restauració")
c, text = crida("POST", "/api/aules", {"nom": "A101", "xarxaCidr": "10.9.9.0/24"}, token=tok_admin)
id_aula = js(text).get("id")
comprova("l'administrador crea l'aula", c in (200, 201) and id_aula, text)
informe = {"nom": "pc19", "integritat": "abc  etc/pam.d/lightdm\n", "arribaPlataforma": "true", "arribaIsard": "true",
           "navegador": "Firefox 155", "discLliureMb": "20000", "uptimeSegons": "3600", "usuarisDins": "0"}
c, _ = crida("POST", "/api/equips/informe", form=informe, ip="10.9.9.8")
comprova("sense testimoni: 401", c == 401)
c, _ = crida("POST", "/api/equips/informe", form=informe, ip="10.9.9.8", cap={"X-Equip-Token": "un-altre"})
comprova("testimoni incorrecte: 401", c == 401)
c, _ = crida("POST", "/api/equips/informe", form=informe, ip="192.168.77.7", cap={"X-Equip-Token": "token-de-prova-1234"})
comprova("IP de fora de qualsevol aula: 403", c == 403)
c, text = crida("POST", "/api/equips/informe", form=informe, ip="10.9.9.8", cap={"X-Equip-Token": "token-de-prova-1234"})
comprova("informe correcte: 200 amb «OK»", c == 200 and text == "OK", text)
c, text = crida("GET", f"/api/aules/{id_aula}/equips", token=tok_admin)
equips = js(text)
comprova("l'administrador veu l'ordinador amb la seva IP", c == 200 and equips["equips"][0]["nom"] == "pc19" and equips["equips"][0]["ip"] == "10.9.9.8", text)
comprova("el professor sense examen en aquella aula: 403", crida("GET", f"/api/aules/{id_aula}/equips", token=tok_prof)[0] == 403)
id_equip = equips["equips"][0]["id"]
c, _ = crida("POST", f"/api/equips/{id_equip}/restaura", token=tok_admin)
comprova("l'administrador demana la restauració (204)", c == 204)
comprova("el professor no pot demanar-la (403)", crida("POST", f"/api/equips/{id_equip}/restaura", token=tok_prof)[0] == 403)
c, text = crida("POST", "/api/equips/informe", form=informe, ip="10.9.9.8", cap={"X-Equip-Token": "token-de-prova-1234"})
comprova("el proper informe rep «RESTAURA» (i res més)", c == 200 and text == "RESTAURA", text)
informe_fet = dict(informe, restauracio="ok")
c, text = crida("POST", "/api/equips/informe", form=informe_fet, ip="10.9.9.8", cap={"X-Equip-Token": "token-de-prova-1234"})
comprova("informe amb el resultat «ok» tanca la petició", c == 200 and text == "OK", text)
equip = js(crida("GET", f"/api/aules/{id_aula}/equips", token=tok_admin)[1])["equips"][0]
comprova("estat: ja no és pendent i el resultat és OK", (not equip["restauracioPendent"]) and equip["restauracioResultat"] == "OK", str(equip))

print("8. Entrades dolentes")
c, _ = crida("POST", "/api/equips/informe", form=dict(informe, nom="../etc/passwd"), ip="10.9.9.8", cap={"X-Equip-Token": "token-de-prova-1234"})
comprova("nom d'ordinador amb caràcters estranys: 400", c == 400)
c, _ = crida("POST", "/api/equips/informe", form=dict(informe, integritat="x" * 150000), ip="10.9.9.8", cap={"X-Equip-Token": "token-de-prova-1234"})
comprova("llista d'integritat massa llarga: 400", c == 400)
c, _ = crida("POST", "/api/auth/login", cos=None, form={"email": "a"}, ip="10.0.0.5")
comprova("login amb un cos que no és JSON: error controlat (no 500)", c in (400, 401, 415))
c, _ = crida("POST", "/api/auth/login", {"email": "x" * 100000, "password": "y"}, ip="10.0.0.6")
comprova("login amb un correu enorme: error controlat (no 500)", c in (400, 401, 413, 429))

print("9. Un professor només veu els alumnes de les seves assignatures")
sql("DELETE FROM matricules; DELETE FROM imparticions; DELETE FROM grups; DELETE FROM moduls; DELETE FROM cicles; DELETE FROM departaments")
sql("INSERT INTO departaments (nom) VALUES ('Informàtica')")
sql("INSERT INTO cicles (codi,nom,departament_id) SELECT 'ASIX','ASIX',id FROM departaments WHERE nom='Informàtica'")
sql("INSERT INTO moduls (codi,nom,cicle_id) SELECT 'M-A','El meu mòdul',id FROM cicles WHERE codi='ASIX'")
sql("INSERT INTO moduls (codi,nom,cicle_id) SELECT 'M-B','Un altre mòdul',id FROM cicles WHERE codi='ASIX'")
for nom, email in [("Ana", "ana@prova.cat"), ("Bernat", "bernat@prova.cat"), ("Carla", "carla@prova.cat"), ("Eva", "eva@prova.cat")]:
    sql(f"INSERT INTO users (name,email,password_hash,role,enabled) VALUES ('{nom}','{email}',crypt('alumne-1234',gen_salt('bf',10)),'STUDENT',true)")
sql("INSERT INTO imparticions (professor_id,modul_id,curs) SELECT u.id,m.id,'2026-27' FROM users u, moduls m WHERE u.email='prof@prova.cat' AND m.codi='M-A'")
sql("INSERT INTO matricules (alumne_id,modul_id,curs) SELECT u.id,m.id,'2026-27' FROM users u, moduls m WHERE u.email='ana@prova.cat' AND m.codi='M-A'")
sql("INSERT INTO matricules (alumne_id,modul_id,curs) SELECT u.id,m.id,'2026-27' FROM users u, moduls m WHERE u.email='bernat@prova.cat' AND m.codi='M-B'")
id_a = sql_valor("SELECT id FROM moduls WHERE codi='M-A'")
id_b = sql_valor("SELECT id FROM moduls WHERE codi='M-B'")
id_bernat = sql_valor("SELECT id FROM users WHERE email='bernat@prova.cat'")
id_ana = sql_valor("SELECT id FROM users WHERE email='ana@prova.cat'")
id_carla = sql_valor("SELECT id FROM users WHERE email='carla@prova.cat'")
# la Carla no és de cap mòdul del professor, però és en un grup seu
c, text = crida("POST", "/api/grups", {"name": "El meu grup"}, token=tok_prof)
id_grup = js(text).get("id")
comprova("el professor crea un grup", c in (200, 201) and id_grup, text)
c, _ = crida("PUT", f"/api/grups/{id_grup}/students", {"studentIds": [id_bernat]}, token=tok_prof)
comprova("no pot afegir a un grup un alumne d'un altre mòdul (403)", c == 403)
c, _ = crida("PUT", f"/api/grups/{id_grup}/students", {"studentIds": [id_ana]}, token=tok_prof)
comprova("sí que pot afegir un alumne del seu mòdul (200)", c == 200)
sql(f"INSERT INTO grup_students (grup_id,student_id) VALUES ('{id_grup}','{id_carla}')")
# un alumne que crea el professor ell mateix
c, text = crida("POST", "/api/users", {"name": "Dani", "email": "dani@prova.cat", "password": "alumne-1234", "role": "STUDENT"}, token=tok_prof)
comprova("el professor dona d'alta un alumne", c in (200, 201), text)
correus = {u["email"] for u in js(crida("GET", "/api/users", token=tok_prof)[1])} if False else {u["email"] for u in json.loads(crida("GET", "/api/users", token=tok_prof)[1])}
comprova("veu l'alumne del seu mòdul", "ana@prova.cat" in correus)
comprova("veu l'alumne del seu grup", "carla@prova.cat" in correus)
comprova("veu l'alumne que ha creat ell", "dani@prova.cat" in correus)
comprova("es veu a si mateix", "prof@prova.cat" in correus)
comprova("NO veu l'alumne d'un altre mòdul", "bernat@prova.cat" not in correus)
comprova("NO veu un alumne sense relació", "eva@prova.cat" not in correus)
comprova("NO veu l'administrador ni altres comptes", "admin@prova.cat" not in correus and "alumne@prova.cat" not in correus, str(sorted(correus)))
rol = json.loads(crida("GET", "/api/users/role/STUDENT", token=tok_prof)[1])
comprova("per rol, només alumnes seus", {u["email"] for u in rol} == {"ana@prova.cat", "carla@prova.cat", "dani@prova.cat"}, str(sorted(u["email"] for u in rol)))
comprova("per rol ADMIN, res", json.loads(crida("GET", "/api/users/role/ADMIN", token=tok_prof)[1]) == [])
tot = {u["email"] for u in json.loads(crida("GET", "/api/users", token=tok_admin)[1])}
comprova("l'administrador ho veu tot", {"ana@prova.cat", "bernat@prova.cat", "eva@prova.cat", "admin@prova.cat"} <= tot)
mat_b = json.loads(crida("GET", f"/api/matricules?modulId={id_b}&curs=2026-27", token=tok_prof)[1])
mat_a = json.loads(crida("GET", f"/api/matricules?modulId={id_a}&curs=2026-27", token=tok_prof)[1])
comprova("matrícules d'un mòdul que no imparteix: cap", mat_b == [])
comprova("matrícules del seu mòdul: l'Ana", [m["alumneNom"] for m in mat_a] == ["Ana"])
comprova("matrícules d'un alumne d'un altre mòdul: cap", json.loads(crida("GET", f"/api/matricules?alumneId={id_bernat}", token=tok_prof)[1]) == [])

print()
if fallades:
    print(f"RESULTAT: {len(fallades)} FALLADES"); [print("  -", f) for f in fallades]; sys.exit(1)
print("RESULTAT: tot correcte")

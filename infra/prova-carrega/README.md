# Prova de càrrega

Simula una classe fent un examen alhora i mesura si el servidor ho aguanta: latència de cada tipus de petició, errors, i quant
triga la correcció del codi en segon pla en entregar. Només fa servir l'API (HTTP), com el navegador.

## Què fa un alumne simulat
Entra, obre l'examen, **desa mentre escriu** (cada 1,5–4 s), **executa codi** (bash i Java), **puja un `.docx`**, de tant en tant «perd el
focus», i **entrega tots cap al final**. Un professor mira el monitor cada 15 s. L'examen té 10 preguntes (4 de test, 2 de text amb
conceptes clau, una comanda bash, un script bash amb test, un programa Java i un lliurament de fitxer).

Els alumnes i l'examen són de prova: l'examen s'activa **només per a ells**, així que els alumnes reals no el veuen.

## Com fer-la

1. **A la màquina de l'aplicació**, crea un administrador de prova (el script el necessita per preparar l'examen i els alumnes).
   Des de `infra/`:
   ```bash
   read -rs -p "Contrasenya de prova: " PW; echo
   docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<SQL
   BEGIN; CREATE EXTENSION IF NOT EXISTS pgcrypto;
   INSERT INTO users (name,email,password_hash,role,enabled)
   VALUES ('Admin prova','carrega-admin@prova.invalid',crypt('$PW',gen_salt('bf',10)),'ADMIN',true);
   DROP EXTENSION pgcrypto; COMMIT;
   SQL
   ```
2. **Des d'un altre ordinador de la xarxa** (no des del servidor, perquè el generador no robi CPU al que es vol mesurar):
   ```bash
   pip install requests
   export ADMIN_EMAIL=carrega-admin@prova.invalid ADMIN_PASSWORD='la contrasenya de dalt'
   python3 prova_carrega.py --url https://examens.centre.cat:3443 --ca ca.crt --alumnes 120
   ```
   Opcions útils: `--alumnes 5 --durada 60` per a una prova ràpida; `--durada` és l'examen **simulat** (360 s per defecte, no 90 minuts).
3. **Neteja** les dades de prova, a la màquina de l'aplicació, des de `infra/`:
   ```bash
   ./prova-carrega/neteja.sh             # només mostra què esborraria
   ./prova-carrega/neteja.sh --esborra   # ho esborra (incloent-hi els fitxers del disc)
   ```

## Què mirar
L'informe (`resultats/prova-….md`) diu si passa els llindars: desar una resposta (p95 ≤ 1 s), entregar (≤ 3 s), executar codi (≤ 20 s),
errors (≤ 1 %), tots els alumnes acaben, i la correcció en segon pla acabada en menys de 5 minuts. Mentre corre, convé mirar el servidor:
`docker stats`, `uptime` i `docker compose logs backend | grep -E "ERROR|Hikari"`.

**Limitacions.** El generador surt d'un sol ordinador (si és per wifi, hi afegeix latència). No simula els 90 minuts reals, sinó una
versió comprimida amb la mateixa càrrega per alumne. Els temps de CPU de l'execució depenen de la màquina.

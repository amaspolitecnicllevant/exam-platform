# Prova de seguretat (contra una instància LOCAL temporal)

Exercita per HTTP les mesures de seguretat de l'aplicació: configuració pública sense llindars, autorització bàsica, renovació i revocació de
sessions, canvi de contrasenya, límit d'intents per compte i adreça, usuari inhabilitat, i els informes d'ordinadors (testimoni, IP d'aula,
estat, restauració) amb entrades dolentes. **No s'ha d'apuntar mai a producció**: crea i esborra usuaris i aules de prova.

```bash
# 1. PostgreSQL temporal
docker run -d --name pg-prova -e POSTGRES_PASSWORD=prova -e POSTGRES_USER=examuser -e POSTGRES_DB=examplatform \
       -p 127.0.0.1:15432:5432 postgres:16-alpine

# 2. L'aplicació (des de backend/, després de «mvn package -DskipTests»)
mkdir -p /tmp/prova/exec /tmp/prova/files
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:15432/examplatform DB_USER=examuser DB_PASS=prova \
JWT_SECRET="$(openssl rand -base64 48)" EQUIPS_TOKEN=token-de-prova-1234 \
SCRIPTS_HOST_PATH=/tmp/prova/exec FILES_HOST_PATH=/tmp/prova/files SERVER_PORT=18080 \
java -Duser.timezone=UTC -jar target/exam-platform-backend-1.0.0-SNAPSHOT.jar &

# 3. La prova (es pot repetir; neteja les dades de prova abans)
python3 infra/prova-seguretat/prova_seguretat.py

# 4. Neteja (el [e] evita que pkill s'aturi a si mateix)
pkill -f "[e]xam-platform-backend"; docker rm -f pg-prova
```

El backend només es refia de `X-Real-IP` si ve d'un proxy intern (`127.x.x.x` ho és): la prova l'usa per simular adreces diferents.
Última execució completa: 56 comprovacions, totes correctes (2026-10-10).

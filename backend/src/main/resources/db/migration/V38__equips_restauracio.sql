-- Restauració d'un ordinador demanada des de l'aplicació: no s'hi envia cap codi. L'aplicació només marca
-- «restauració pendent»; l'ordinador, en el seu proper informe, ho llegeix i executa la seva còpia local de
-- l'script de preparació (idempotent), i després n'informa el resultat.
ALTER TABLE equips_aula ADD COLUMN restauracio_demanada_el TIMESTAMP;
ALTER TABLE equips_aula ADD COLUMN restauracio_resultat VARCHAR(200);
ALTER TABLE equips_aula ADD COLUMN restauracio_resultat_el TIMESTAMP;

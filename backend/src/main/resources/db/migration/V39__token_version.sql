-- Versió dels tokens de sessió d'un usuari. Es puja en canviar o reinicialitzar la contrasenya, o quan un administrador
-- tanca les seves sessions: tots els tokens emesos abans deixen de ser vàlids.
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

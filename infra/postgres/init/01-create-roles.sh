#!/bin/bash
# Cria os 3 papeis de banco do Estokio (ver estokio-vault/02-Decisoes/Decisoes Registradas.md #10):
#   migrator      -> dono do schema, unico role que roda as migrations Flyway.
#   app_user      -> usado pela aplicacao fora de /api/plataforma/**, sujeito a RLS.
#   app_platform  -> usado so em /api/plataforma/**, BYPASSRLS, restrito a queries agregadas
#                    pela camada de servico (o banco so garante que ele PODE ver tudo).
#
# Os GRANT/REVOKE finos por tabela (SELECT/INSERT/UPDATE/DELETE, REVOKE em estoque_movimento,
# RLS policies) ficam nas migrations Flyway, porque so existem depois que o `migrator` cria
# as tabelas. Este script so cria os roles e da a eles acesso ao banco/schema.
set -euo pipefail

: "${ESTOKIO_MIGRATOR_PASSWORD:?ESTOKIO_MIGRATOR_PASSWORD precisa estar definida}"
: "${ESTOKIO_APP_USER_PASSWORD:?ESTOKIO_APP_USER_PASSWORD precisa estar definida}"
: "${ESTOKIO_APP_PLATFORM_PASSWORD:?ESTOKIO_APP_PLATFORM_PASSWORD precisa estar definida}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE migrator LOGIN PASSWORD '${ESTOKIO_MIGRATOR_PASSWORD}';
    CREATE ROLE app_user LOGIN PASSWORD '${ESTOKIO_APP_USER_PASSWORD}';
    CREATE ROLE app_platform LOGIN PASSWORD '${ESTOKIO_APP_PLATFORM_PASSWORD}' BYPASSRLS;

    GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO migrator, app_user, app_platform;

    -- migrator passa a ser dono do schema public: ganha CREATE implicitamente e passa a ser
    -- dono de toda tabela que criar via Flyway, podendo conceder privilegios a app_user/app_platform
    -- de dentro das proprias migrations.
    ALTER SCHEMA public OWNER TO migrator;
    GRANT USAGE ON SCHEMA public TO app_user, app_platform;
EOSQL

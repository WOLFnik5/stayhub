#!/usr/bin/env bash
(
set -Eeuo pipefail

: "${POSTGRES_USER:?Bootstrap administrator is required}"
: "${POSTGRES_DB:?Database name is required}"
: "${DB_USERNAME:?Runtime role is required}"
: "${DB_PASSWORD:?Runtime password is required}"
: "${LIQUIBASE_USERNAME:?Migration role is required}"
: "${LIQUIBASE_PASSWORD:?Migration password is required}"
[[ "$DB_PASSWORD" != "$LIQUIBASE_PASSWORD"
    && "$DB_PASSWORD" != "${POSTGRES_PASSWORD:-}"
    && "$LIQUIBASE_PASSWORD" != "${POSTGRES_PASSWORD:-}" ]] || {
    echo 'Bootstrap, runtime and migration passwords must differ' >&2; exit 2;
}
[[ "$DB_USERNAME" != "$POSTGRES_USER" && "$LIQUIBASE_USERNAME" != "$POSTGRES_USER"
    && "$DB_USERNAME" != "$LIQUIBASE_USERNAME" ]] || {
    echo 'Bootstrap, runtime and migration roles must differ' >&2; exit 2;
}
for role in "$DB_USERNAME" "$LIQUIBASE_USERNAME"; do
    [[ "$role" =~ ^[a-z][a-z0-9_]{0,62}$ && "$role" != pg_* ]] || {
        echo 'Application role names must be simple lowercase identifiers' >&2; exit 2;
    }
done

# Credentials stay in the environment, never in command-line arguments or echoed SQL.
psql -X --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\set ECHO none
\getenv runtime_role DB_USERNAME
\getenv runtime_password DB_PASSWORD
\getenv migration_role LIQUIBASE_USERNAME
\getenv migration_password LIQUIBASE_PASSWORD
SELECT rolsuper AS is_admin FROM pg_roles WHERE rolname = current_user \gset
\if :is_admin
\else
  \echo 'Provisioning requires a PostgreSQL administrator'
  \quit 2
\endif
SELECT to_regclass('public.databasechangelog') IS NOT NULL
   AND to_regclass('liquibase.databasechangelog') IS NOT NULL AS conflicting_history \gset
\if :conflicting_history
  \echo 'Both Liquibase histories exist; resolve their provenance before provisioning'
  \quit 2
\endif

BEGIN;
SELECT format('CREATE ROLE %I LOGIN', :'migration_role')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'migration_role') \gexec
SELECT format('CREATE ROLE %I LOGIN', :'runtime_role')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'runtime_role') \gexec
SELECT format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
              :'migration_role', :'migration_password') \gexec
SELECT format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
              :'runtime_role', :'runtime_password') \gexec
-- Remove inherited role membership as well as direct administrator attributes.
SELECT format('REVOKE %I FROM %I', granted.rolname, member.rolname)
FROM pg_auth_members membership
JOIN pg_roles granted ON granted.oid = membership.roleid
JOIN pg_roles member ON member.oid = membership.member
WHERE member.rolname IN (:'runtime_role', :'migration_role') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database()) \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', current_database(), current_user) \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM %I', current_database(), :'runtime_role') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'runtime_role') \gexec
SELECT format('GRANT CONNECT, CREATE, TEMPORARY ON DATABASE %I TO %I',
              current_database(), :'migration_role') \gexec
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('CREATE SCHEMA IF NOT EXISTS liquibase AUTHORIZATION %I', :'migration_role') \gexec
SELECT format('ALTER SCHEMA liquibase OWNER TO %I', :'migration_role') \gexec
REVOKE ALL ON SCHEMA liquibase FROM PUBLIC;
SELECT format('REVOKE ALL ON SCHEMA liquibase FROM %I', :'runtime_role') \gexec
SELECT format('ALTER SCHEMA public OWNER TO %I', :'migration_role') \gexec
SELECT format('REVOKE ALL ON SCHEMA public FROM %I', :'runtime_role') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'runtime_role') \gexec
SELECT 'ALTER TABLE public.' || name || ' SET SCHEMA liquibase'
FROM (VALUES ('databasechangelog'), ('databasechangeloglock')) AS metadata(name)
WHERE to_regclass('public.' || name) IS NOT NULL \gexec
SELECT format('ALTER TABLE %I.%I OWNER TO %I', schemaname, tablename, :'migration_role')
FROM pg_tables WHERE schemaname IN ('public', 'liquibase') \gexec
SELECT format('ALTER SEQUENCE %I.%I OWNER TO %I', sequence_schema, sequence_name, :'migration_role')
FROM information_schema.sequences WHERE sequence_schema = 'public' \gexec
SELECT format('ALTER FUNCTION %s OWNER TO %I', procedure.oid::regprocedure, :'migration_role')
FROM pg_proc procedure JOIN pg_namespace namespace ON namespace.oid = procedure.pronamespace
WHERE namespace.nspname = 'public'
  AND NOT EXISTS (SELECT 1 FROM pg_depend WHERE classid = 'pg_proc'::regclass
                  AND objid = procedure.oid AND deptype = 'e') \gexec
SELECT format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', :'runtime_role') \gexec
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA liquibase FROM PUBLIC;
SELECT format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', :'runtime_role') \gexec
SELECT format('REVOKE ALL ON ALL TABLES IN SCHEMA liquibase FROM %I', :'runtime_role') \gexec
SELECT format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I',
              :'runtime_role') \gexec
SELECT format('GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO %I', :'runtime_role') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I REVOKE ALL ON TABLES FROM PUBLIC, %I',
              :'migration_role', :'runtime_role') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public REVOKE ALL ON TABLES FROM %I',
              :'migration_role', :'runtime_role') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I REVOKE ALL ON SEQUENCES FROM PUBLIC, %I',
              :'migration_role', :'runtime_role') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I',
              :'migration_role', :'runtime_role') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
              :'migration_role', :'runtime_role') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT USAGE ON SEQUENCES TO %I',
              :'migration_role', :'runtime_role') \gexec
SELECT format('ALTER ROLE %I SET search_path = public', :'runtime_role') \gexec
SELECT format('ALTER ROLE %I SET search_path = public', :'migration_role') \gexec
COMMIT;
SQL
)

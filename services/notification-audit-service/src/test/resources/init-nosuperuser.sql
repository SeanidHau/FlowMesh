CREATE USER flowmesh_audit LOGIN PASSWORD 'change-me-audit' NOSUPERUSER NOINHERIT;
CREATE ROLE flowmesh_audit_migrator LOGIN PASSWORD 'change-me-audit-migrator'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE ROLE flowmesh_audit_delivery NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT BYPASSRLS;
GRANT flowmesh_audit_delivery TO flowmesh_audit;
GRANT flowmesh_audit_delivery TO flowmesh_audit_migrator;
CREATE SCHEMA audit AUTHORIZATION flowmesh_audit_migrator;
GRANT USAGE, CREATE ON SCHEMA audit TO flowmesh_audit_migrator;
GRANT USAGE ON SCHEMA audit TO flowmesh_audit;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_audit_migrator IN SCHEMA audit
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_audit;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_audit_migrator IN SCHEMA audit
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_audit;
ALTER ROLE flowmesh_audit SET search_path TO audit, public;

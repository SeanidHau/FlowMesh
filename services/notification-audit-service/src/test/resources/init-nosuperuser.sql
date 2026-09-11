CREATE USER flowmesh_audit LOGIN PASSWORD 'change-me-audit' NOSUPERUSER;
CREATE SCHEMA audit AUTHORIZATION flowmesh_audit;
GRANT USAGE, CREATE ON SCHEMA audit TO flowmesh_audit;
ALTER ROLE flowmesh_audit SET search_path TO audit, public;

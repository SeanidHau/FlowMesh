CREATE USER flowmesh_risk LOGIN PASSWORD 'change-me-risk' NOSUPERUSER;
CREATE ROLE flowmesh_risk_migrator LOGIN PASSWORD 'change-me-risk-migrator'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE SCHEMA risk AUTHORIZATION flowmesh_risk_migrator;
GRANT USAGE, CREATE ON SCHEMA risk TO flowmesh_risk_migrator;
GRANT USAGE ON SCHEMA risk TO flowmesh_risk;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_risk_migrator IN SCHEMA risk
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_risk;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_risk_migrator IN SCHEMA risk
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_risk;
ALTER ROLE flowmesh_risk SET search_path TO risk, public;

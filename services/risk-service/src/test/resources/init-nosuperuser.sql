CREATE USER flowmesh_risk LOGIN PASSWORD 'change-me-risk' NOSUPERUSER;
CREATE SCHEMA risk AUTHORIZATION flowmesh_risk;
GRANT USAGE, CREATE ON SCHEMA risk TO flowmesh_risk;
ALTER ROLE flowmesh_risk SET search_path TO risk, public;

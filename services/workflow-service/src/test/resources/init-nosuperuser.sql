CREATE ROLE flowmesh_workflow LOGIN PASSWORD 'change-me-workflow' NOSUPERUSER;
CREATE ROLE flowmesh_workflow_migrator LOGIN PASSWORD 'change-me-workflow-migrator'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE ROLE flowmesh_workflow_sla LOGIN PASSWORD 'change-me-workflow-sla'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT BYPASSRLS;
CREATE SCHEMA IF NOT EXISTS workflow AUTHORIZATION flowmesh_workflow_migrator;
GRANT USAGE, CREATE ON SCHEMA workflow TO flowmesh_workflow_migrator;
GRANT USAGE ON SCHEMA workflow TO flowmesh_workflow;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_workflow_migrator IN SCHEMA workflow
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowmesh_workflow;
ALTER DEFAULT PRIVILEGES FOR ROLE flowmesh_workflow_migrator IN SCHEMA workflow
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO flowmesh_workflow;

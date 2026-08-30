CREATE ROLE flowmesh_workflow LOGIN PASSWORD 'change-me-workflow' NOSUPERUSER;
CREATE SCHEMA IF NOT EXISTS workflow AUTHORIZATION flowmesh_workflow;
GRANT ALL ON SCHEMA workflow TO flowmesh_workflow;

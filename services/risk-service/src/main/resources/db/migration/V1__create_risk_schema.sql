-- V1: 风控结果和可靠事件 Outbox。

CREATE TABLE risk_results (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    application_id UUID NOT NULL,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('PASS', 'REJECT')),
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_risk_results_application UNIQUE (tenant_id, application_id)
);

CREATE TABLE risk_outbox_events (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    topic VARCHAR(128) NOT NULL,
    tag VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    claimed_until TIMESTAMP WITH TIME ZONE,
    claim_token UUID,
    published_at TIMESTAMP WITH TIME ZONE,
    dead_lettered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_risk_outbox_available
    ON risk_outbox_events (next_attempt_at, created_at)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;

ALTER TABLE risk_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE risk_results FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_risk_results ON risk_results
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::text);

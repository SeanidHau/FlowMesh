CREATE TABLE tenants (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_tenants_status CHECK ( status IN ('ACTIVE', 'DISABLED') )
);

CREATE TABLE iam_users (
  id UUID PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  last_login_at TIMESTAMP WITH TIME ZONE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_iam_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
  CONSTRAINT uk_iam_users_tenant_username UNIQUE (tenant_id, username),
  CONSTRAINT ck_iam_users_status CHECK ( status IN ('ACTIVE', 'DISABLED', 'LOCKED') )
);

CREATE TABLE iam_roles (
  id UUID PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uk_iam_roles_code UNIQUE (code)
);

CREATE TABLE iam_user_roles (
  user_id UUID NOT NULL,
  role_id UUID NOT NULL,
  assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_iam_user_roles_user FOREIGN KEY (user_id) REFERENCES iam_users (id),
  CONSTRAINT fk_iam_user_roles_role FOREIGN KEY (role_id) REFERENCES iam_roles (id)
);

CREATE TABLE iam_refresh_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  revoked_at TIMESTAMP WITH TIME ZONE,
  replaced_by_token_id UUID,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_iam_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES iam_users (id),
  CONSTRAINT fk_iam_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_token_id) REFERENCES iam_refresh_tokens (id),
  CONSTRAINT uk_iam_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_iam_users_tenant_id ON iam_users (tenant_id);
CREATE INDEX idx_iam_refresh_tokens_user_id ON iam_refresh_tokens (user_id);
CREATE INDEX idx_iam_refresh_tokens_expires_at ON iam_refresh_tokens (expires_at);

-- 作用：为 Refresh Token 定期清理建立撤销时间索引，降低长期运行后的清理扫描成本。
CREATE INDEX idx_iam_refresh_tokens_revoked_at
    ON iam_refresh_tokens (revoked_at)
    WHERE revoked_at IS NOT NULL;

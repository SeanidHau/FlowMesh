ALTER TABLE iam_refresh_tokens
    ADD CONSTRAINT uk_iam_refresh_tokens_replaced_by_token
    UNIQUE (replaced_by_token_id);

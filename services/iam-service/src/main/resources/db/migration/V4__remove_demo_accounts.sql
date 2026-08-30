-- V4: 从所有共享环境移除版本化迁移中的公开演示账号。
-- 本地演示账号由 FLOWMESH_DEMO_DATA_ENABLED=true 的显式初始化器补充。
DELETE FROM iam_refresh_tokens
WHERE user_id IN (
    SELECT id FROM iam_users
    WHERE username IN ('applicant-a', 'purchaser-a', 'legal-a', 'finance-a', 'operations', 'applicant-b', 'purchaser-b')
);

DELETE FROM iam_user_roles
WHERE user_id IN (
    SELECT id FROM iam_users
    WHERE username IN ('applicant-a', 'purchaser-a', 'legal-a', 'finance-a', 'operations', 'applicant-b', 'purchaser-b')
);

DELETE FROM iam_users
WHERE username IN ('applicant-a', 'purchaser-a', 'legal-a', 'finance-a', 'operations', 'applicant-b', 'purchaser-b');

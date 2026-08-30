-- V3: 双租户种子数据
-- 包含 2 个租户、5 个全局角色、每租户若干用户（用户名全局唯一），密码统一 password123 的 BCrypt 哈希。

-- 租户
INSERT INTO tenants (id, name, status, created_at, updated_at) VALUES
  ('tenant-a', '租户 A（采购方）', 'ACTIVE', now(), now()),
  ('tenant-b', '租户 B（供应商方）', 'ACTIVE', now(), now());

-- 角色（全局，code 唯一）
INSERT INTO iam_roles (id, code, name, description, created_at) VALUES
  ('a0000000-0000-0000-0000-000000000001', 'APPLICANT', '供应商申请人', '提交供应商准入申请', now()),
  ('a0000000-0000-0000-0000-000000000002', 'PURCHASER', '采购审批人', '执行采购初审', now()),
  ('a0000000-0000-0000-0000-000000000003', 'LEGAL', '法务审批人', '法务合规会签', now()),
  ('a0000000-0000-0000-0000-000000000004', 'FINANCE', '财务审批人', '财务风控会签', now()),
  ('a0000000-0000-0000-0000-000000000005', 'OPERATIONS', '运营管理员', '供应商启用与运营处置', now());

-- 用户（用户名全局唯一，密码统一 BCrypt(password123)）
-- tenant-a 用户
INSERT INTO iam_users (id, tenant_id, username, password_hash, display_name, status, last_login_at, version, created_at, updated_at) VALUES
  ('b0000000-0000-0000-0000-000000000001', 'tenant-a', 'applicant-a', '$2a$10$rORwMfloMt7BhZVGlZNRluzFFkeQd.0DZZofzqjXug1ZZO8ow4RHa', '申请人A', 'ACTIVE', NULL, 0, now(), now()),
  ('b0000000-0000-0000-0000-000000000002', 'tenant-a', 'purchaser-a',  '$2a$10$rORwMfloMt7BhZVGlZNRluzFFkeQd.0DZZofzqjXug1ZZO8ow4RHa', '采购人A', 'ACTIVE', NULL, 0, now(), now()),
  ('b0000000-0000-0000-0000-000000000003', 'tenant-a', 'legal-a',      '$2a$10$rORwMfloMt7BhZVGlZNRluzFFkeQd.0DZZofzqjXug1ZZO8ow4RHa', '法务A',   'ACTIVE', NULL, 0, now(), now()),
  ('b0000000-0000-0000-0000-000000000004', 'tenant-a', 'finance-a',    '$2a$10$rORwMfloMt7BhZVGlZNRluzFFkeQd.0DZZofzqjXug1ZZO8ow4RHa', '财务A',   'ACTIVE', NULL, 0, now(), now()),
  ('b0000000-0000-0000-0000-000000000005', 'tenant-a', 'operations',   '$2a$10$rORwMfloMt7BhZVGlZNRluzFFkeQd.0DZZofzqjXug1ZZO8ow4RHa', '运营',    'ACTIVE', NULL, 0, now(), now());

-- tenant-b 用户
INSERT INTO iam_users (id, tenant_id, username, password_hash, display_name, status, last_login_at, version, created_at, updated_at) VALUES
  ('b0000000-0000-0000-0000-000000000006', 'tenant-b', 'applicant-b',  '$2a$10$rORwMfloMt7BhZVGlZNRluzFFkeQd.0DZZofzqjXug1ZZO8ow4RHa', '申请人B', 'ACTIVE', NULL, 0, now(), now()),
  ('b0000000-0000-0000-0000-000000000007', 'tenant-b', 'purchaser-b',  '$2a$10$rORwMfloMt7BhZVGlZNRluzFFkeQd.0DZZofzqjXug1ZZO8ow4RHa', '采购人B', 'ACTIVE', NULL, 0, now(), now());

-- 用户-角色绑定
-- tenant-a: applicant-a → APPLICANT, purchaser-a → PURCHASER, legal-a → LEGAL, finance-a → FINANCE, operations → OPERATIONS
INSERT INTO iam_user_roles (user_id, role_id, assigned_at) VALUES
  ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', now()),
  ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000002', now()),
  ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000003', now()),
  ('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000004', now()),
  ('b0000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000005', now()),
-- tenant-b: applicant-b → APPLICANT, purchaser-b → PURCHASER
  ('b0000000-0000-0000-0000-000000000006', 'a0000000-0000-0000-0000-000000000001', now()),
  ('b0000000-0000-0000-0000-000000000007', 'a0000000-0000-0000-0000-000000000002', now());

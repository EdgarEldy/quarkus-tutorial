-- V2: baseline RBAC data. Without it nobody could ever be granted ROLE:WRITE or PERMISSION:WRITE
-- to create the first assignment. Idempotent so a re-run never duplicates rows.

INSERT INTO permissions (resource, action)
SELECT r.resource, a.action
FROM (VALUES ('USER'), ('ROLE'), ('PERMISSION'), ('CATEGORY'), ('PRODUCT'), ('CUSTOMER'), ('ORDER')) AS r (resource)
CROSS JOIN (VALUES ('READ'), ('WRITE')) AS a (action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO roles (role_name) VALUES ('ADMIN')
ON CONFLICT (role_name) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- Migration V11: Add KTV (Content Moderator / Technician) Role
-- ============================================================================

-- 1. Update check constraint on roles table to permit 'KTV'
ALTER TABLE roles DROP CONSTRAINT IF EXISTS ck_roles_name;
ALTER TABLE roles ADD CONSTRAINT ck_roles_name CHECK (role_name IN ('BUYER', 'SELLER', 'ADMIN', 'KTV'));

-- 2. Insert KTV role with role_id = 4 (or next identity)
INSERT INTO roles (role_id, role_name, description)
VALUES (4, 'KTV', 'Kiểm tra viên / Content moderator role')
ON CONFLICT (role_name) DO UPDATE SET description = EXCLUDED.description;

-- Update identity sequence if needed
SELECT setval('roles_role_id_seq', GREATEST(4, (SELECT coalesce(max(role_id), 4) FROM roles)));

-- 3. Update existing ktv@gmail.com account role to KTV if present
DO $$
DECLARE
    v_ktv_role_id SMALLINT;
    v_ktv_user_id BIGINT;
    v_admin_id BIGINT;
BEGIN
    SELECT role_id INTO v_ktv_role_id FROM roles WHERE role_name = 'KTV';
    SELECT user_id INTO v_ktv_user_id FROM users WHERE email = 'ktv@gmail.com';
    SELECT user_id INTO v_admin_id FROM users WHERE email = 'admin@gmail.com';

    IF v_ktv_user_id IS NOT NULL AND v_ktv_role_id IS NOT NULL THEN
        -- Remove previous BUYER role
        DELETE FROM user_roles WHERE user_id = v_ktv_user_id AND role_id = (SELECT role_id FROM roles WHERE role_name = 'BUYER');

        -- Assign KTV role
        INSERT INTO user_roles (user_id, role_id, granted_at, granted_by)
        VALUES (v_ktv_user_id, v_ktv_role_id, CURRENT_TIMESTAMP, v_admin_id)
        ON CONFLICT (user_id, role_id) DO NOTHING;

        -- Update display name
        UPDATE users
        SET full_name = '[TEST] Kiểm tra viên'
        WHERE user_id = v_ktv_user_id AND full_name LIKE '%quyền tạm BUYER%';
    END IF;
END $$;

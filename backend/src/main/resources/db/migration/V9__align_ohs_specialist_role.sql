-- =====================================================================================
-- V9 - Align OHS specialist role code with application authorization standard
--
-- Owner: Backend 1
--
-- Preserve the existing role ID so user_roles foreign-key relationships remain intact.
-- =====================================================================================

UPDATE roles
SET name = 'OHS_SPECIALIST'
WHERE name = 'ISG_EXPERT';
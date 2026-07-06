-- ============================================================
-- V004: island_types diventa sincronizzabile bidirezionalmente
-- Aggiunge is_deleted/synced_at per supportare create/update/
-- soft-delete dei tipi isola dai client (Android), non solo
-- lettura server-authoritative.
-- ============================================================

ALTER TABLE island_types
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE island_types
    ADD COLUMN IF NOT EXISTS synced_at BIGINT;

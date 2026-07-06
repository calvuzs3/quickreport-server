-- ============================================================
-- V010: photos
--
-- Metadati foto dei check item. Sincronizzate in blocco insieme
-- ai check_items del checkup proprietario (stesso pattern di V008):
-- niente updated_at/synced_at/is_deleted per-riga, la tabella viene
-- sostituita in blocco per checkup ad ogni sync.
--
-- I byte dei file viaggiano fuori da qui, tramite gli endpoint
-- /photos/manifest, /photos/upload/{id}, /photos/download/{id}
-- (vedi PhotoRoutes.kt) — l'hash per il diff si calcola al volo sui
-- bytes, non è persistito.
-- ============================================================

CREATE TABLE IF NOT EXISTS photos (
    id             TEXT PRIMARY KEY,
    check_item_id  TEXT NOT NULL,
    file_name      TEXT NOT NULL,
    caption        TEXT NOT NULL DEFAULT '',
    taken_at       BIGINT NOT NULL,
    file_size      BIGINT NOT NULL,
    order_index    INTEGER NOT NULL DEFAULT 0,
    width          INTEGER NOT NULL DEFAULT 0,
    height         INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_photos_check_item ON photos (check_item_id);

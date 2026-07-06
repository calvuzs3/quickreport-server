-- ============================================================
-- V008: check_items
--
-- Voci di checkup (risultati delle ispezioni), distinte dai
-- check_item_templates (master data). Ogni riga appartiene a
-- un checkup specifico e viene sincronizzata insieme al checkup.
-- ============================================================

CREATE TABLE IF NOT EXISTS check_items (
    id              TEXT PRIMARY KEY,
    checkup_id      TEXT NOT NULL,
    module_type     TEXT NOT NULL,
    module_type_id  TEXT,
    item_code       TEXT NOT NULL,
    description     TEXT NOT NULL,
    status          TEXT NOT NULL,
    criticality     TEXT NOT NULL,
    criticality_id  TEXT,
    notes           TEXT NOT NULL DEFAULT '',
    checked_at      BIGINT,
    order_index     INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_check_items_checkup ON check_items (checkup_id);

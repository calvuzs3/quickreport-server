-- ============================================================
-- V003: Normalizzazione island_types
-- Crea la tabella canonical island_types, seed 10 tipi,
-- aggiunge colonna FK nullable a facility_islands e backfilla.
-- Eseguire su PostgreSQL PRIMA del deploy del codice Ktor.
-- ============================================================

-- 1. Crea la tabella canonical
CREATE TABLE IF NOT EXISTS island_types (
    id                        TEXT PRIMARY KEY,
    code                      TEXT NOT NULL,
    label                     TEXT NOT NULL,
    description               TEXT,
    icon_name                 TEXT,
    maintenance_interval_days INTEGER NOT NULL DEFAULT 180,
    sort_order                INTEGER NOT NULL DEFAULT 0,
    is_active                 BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                BIGINT NOT NULL,
    updated_at                BIGINT NOT NULL,
    CONSTRAINT uq_island_types_code UNIQUE (code)
);

-- 2. Seed: union di tutti i tipi Android (7) + Web (3 extra) = 10 tipi
--    maintenance_interval_days: POLY_TAG_BLE=90, tutti gli altri=180
INSERT INTO island_types (id, code, label, description, icon_name, maintenance_interval_days, sort_order, is_active, created_at, updated_at)
VALUES
    (gen_random_uuid()::text, 'POLY_MOVE',    'POLY Move',    'Isola di movimentazione',       'directions_car',  180, 10,  TRUE, 1750118400000, 1750118400000),
    (gen_random_uuid()::text, 'POLY_CAST',    'POLY Cast',    'Isola di fusione/colata',       'build',           180, 20,  TRUE, 1750118400000, 1750118400000),
    (gen_random_uuid()::text, 'POLY_EBT',     'POLY EBT',     'Isola EBT',                     'electric_bolt',   180, 30,  TRUE, 1750118400000, 1750118400000),
    (gen_random_uuid()::text, 'POLY_TAG_BLE', 'POLY Tag BLE', 'Isola di tagging BLE',          'label',            90, 40,  TRUE, 1750118400000, 1750118400000),
    (gen_random_uuid()::text, 'POLY_TAG_FC',  'POLY Tag FC',  'Isola di tagging contactless',  'qr_code',         180, 50,  TRUE, 1750118400000, 1750118400000),
    (gen_random_uuid()::text, 'POLY_TAG_V',   'POLY Tag V',   'Isola di tagging con visione',  'camera_alt',      180, 60,  TRUE, 1750118400000, 1750118400000),
    (gen_random_uuid()::text, 'POLY_SAMPLE',  'POLY Sample',  'Isola di campionamento',        'science',         180, 70,  TRUE, 1750118400000, 1750118400000),
    (gen_random_uuid()::text, 'POLY_WELD',    'POLY Weld',    'Isola di saldatura',            'hardware',        180, 80,  TRUE, 1750118400000, 1750118400000),
    (gen_random_uuid()::text, 'POLY_PAINT',   'POLY Paint',   'Isola di verniciatura',         'format_paint',    180, 90,  TRUE, 1750118400000, 1750118400000),
    (gen_random_uuid()::text, 'OTHER',        'Altro',        'Tipologia non classificata',    'category',        180, 100, TRUE, 1750118400000, 1750118400000)
ON CONFLICT (code) DO NOTHING;

-- 3. Aggiunge colonna FK nullable a facility_islands (Expand — nulla viene rimosso)
ALTER TABLE facility_islands
    ADD COLUMN IF NOT EXISTS island_type_id TEXT REFERENCES island_types(id);

-- 4. Backfill island_type_id dai codici esistenti
UPDATE facility_islands fi
SET island_type_id = it.id
FROM island_types it
WHERE fi.island_type = it.code
  AND fi.island_type_id IS NULL;

-- 5. Indice per query per tipo
CREATE INDEX IF NOT EXISTS idx_facility_islands_island_type_id
    ON facility_islands(island_type_id);

-- Verifica post-migrazione (eseguire manualmente per controllo):
-- SELECT COUNT(*) FROM facility_islands WHERE island_type_id IS NULL;
-- Deve restituire 0 se tutti i codici esistenti erano validi.

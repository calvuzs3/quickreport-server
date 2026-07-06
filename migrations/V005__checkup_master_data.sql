-- ============================================================
-- V005: Checkup master data tables + role support in auth_users
--
-- Aggiunge 4 tabelle master per il checkup (module_types,
-- criticality_levels, checkup_statuses, check_item_templates)
-- e un campo 'role' sulla tabella auth_users per gestire il
-- permesso di push (solo ADMIN).
--
-- Le tabelle seguono lo stesso schema di island_types:
--   synced_at + is_deleted per il protocollo pull/push.
-- ============================================================

-- 1. auth_users: aggiungi colonna role
ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'TECHNICIAN';

-- ============================================================
-- 2. module_types
-- ============================================================
CREATE TABLE IF NOT EXISTS module_types (
    id          TEXT PRIMARY KEY,
    code        TEXT NOT NULL,
    label       TEXT NOT NULL,
    description TEXT,
    icon_name   TEXT,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  BIGINT  NOT NULL,
    updated_at  BIGINT  NOT NULL,
    synced_at   BIGINT,
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_module_types_code UNIQUE (code)
);

-- Seed: valori di default (mirror enum ModuleType Android)
INSERT INTO module_types (id, code, label, sort_order, is_active, created_at, updated_at) VALUES
    ('MECHANICAL', 'MECHANICAL', 'Meccanico',  1, TRUE, 1704067200000, 1704067200000),
    ('SAFETY',     'SAFETY',     'Sicurezza',  2, TRUE, 1704067200000, 1704067200000),
    ('ELECTRICAL', 'ELECTRICAL', 'Elettrico',  3, TRUE, 1704067200000, 1704067200000),
    ('SOFTWARE',   'SOFTWARE',   'Software',   4, TRUE, 1704067200000, 1704067200000),
    ('PNEUMATIC',  'PNEUMATIC',  'Pneumatico', 5, TRUE, 1704067200000, 1704067200000),
    ('HYDRAULIC',  'HYDRAULIC',  'Idraulico',  6, TRUE, 1704067200000, 1704067200000),
    ('STRUCTURAL', 'STRUCTURAL', 'Strutturale',7, TRUE, 1704067200000, 1704067200000)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 3. criticality_levels
-- ============================================================
CREATE TABLE IF NOT EXISTS criticality_levels (
    id          TEXT PRIMARY KEY,
    code        TEXT NOT NULL,
    label       TEXT NOT NULL,
    priority    INTEGER NOT NULL DEFAULT 0,
    color_hex   TEXT NOT NULL DEFAULT '#808080',
    icon_emoji  TEXT,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  BIGINT  NOT NULL,
    updated_at  BIGINT  NOT NULL,
    synced_at   BIGINT,
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_criticality_levels_code UNIQUE (code)
);

INSERT INTO criticality_levels (id, code, label, priority, color_hex, icon_emoji, sort_order, is_active, created_at, updated_at) VALUES
    ('CRITICAL', 'CRITICAL', 'Critico', 4, '#D32F2F', '🔴', 1, TRUE, 1704067200000, 1704067200000),
    ('HIGH',     'HIGH',     'Alto',    3, '#F57C00', '🟠', 2, TRUE, 1704067200000, 1704067200000),
    ('MEDIUM',   'MEDIUM',   'Medio',   2, '#FBC02D', '🟡', 3, TRUE, 1704067200000, 1704067200000),
    ('LOW',      'LOW',      'Basso',   1, '#388E3C', '🟢', 4, TRUE, 1704067200000, 1704067200000),
    ('ROUTINE',  'ROUTINE',  'Routine', 0, '#1976D2', '🔵', 5, TRUE, 1704067200000, 1704067200000)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 4. checkup_statuses
-- ============================================================
CREATE TABLE IF NOT EXISTS checkup_statuses (
    id               TEXT PRIMARY KEY,
    code             TEXT NOT NULL,
    label            TEXT NOT NULL,
    color_hex        TEXT NOT NULL DEFAULT '#808080',
    icon_emoji       TEXT,
    sort_order       INTEGER NOT NULL DEFAULT 0,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    blocks_deletion  BOOLEAN NOT NULL DEFAULT FALSE,
    marks_completion BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       BIGINT  NOT NULL,
    updated_at       BIGINT  NOT NULL,
    synced_at        BIGINT,
    is_deleted       BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_checkup_statuses_code UNIQUE (code)
);

INSERT INTO checkup_statuses (id, code, label, color_hex, icon_emoji, sort_order, is_active, blocks_deletion, marks_completion, created_at, updated_at) VALUES
    ('DRAFT',       'DRAFT',       'Bozza',       '#9E9E9E', '📝', 1, TRUE, FALSE, FALSE, 1704067200000, 1704067200000),
    ('IN_PROGRESS', 'IN_PROGRESS', 'In corso',    '#1976D2', '🔄', 2, TRUE, FALSE, FALSE, 1704067200000, 1704067200000),
    ('COMPLETED',   'COMPLETED',   'Completato',  '#388E3C', '✅', 3, TRUE, TRUE,  TRUE,  1704067200000, 1704067200000),
    ('EXPORTED',    'EXPORTED',    'Esportato',   '#7B1FA2', '📤', 4, TRUE, TRUE,  FALSE, 1704067200000, 1704067200000),
    ('ARCHIVED',    'ARCHIVED',    'Archiviato',  '#455A64', '🗄️', 5, TRUE, TRUE,  FALSE, 1704067200000, 1704067200000)
ON CONFLICT (id) DO NOTHING;

-- Tabella di transizioni (permette di sapere da quale stato si può andare a quale)
CREATE TABLE IF NOT EXISTS checkup_status_transitions (
    from_status_id TEXT NOT NULL,
    to_status_id   TEXT NOT NULL,
    PRIMARY KEY (from_status_id, to_status_id)
);

INSERT INTO checkup_status_transitions (from_status_id, to_status_id) VALUES
    ('DRAFT',       'IN_PROGRESS'),
    ('IN_PROGRESS', 'DRAFT'),
    ('IN_PROGRESS', 'COMPLETED'),
    ('COMPLETED',   'IN_PROGRESS'),
    ('COMPLETED',   'EXPORTED'),
    ('COMPLETED',   'ARCHIVED'),
    ('EXPORTED',    'ARCHIVED')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 5. check_item_templates
-- ============================================================
CREATE TABLE IF NOT EXISTS check_item_templates (
    id              TEXT PRIMARY KEY,
    module_type_id  TEXT NOT NULL,
    category        TEXT NOT NULL DEFAULT '',
    description     TEXT NOT NULL,
    criticality_id  TEXT NOT NULL,
    order_index     INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      BIGINT  NOT NULL,
    updated_at      BIGINT  NOT NULL,
    synced_at       BIGINT,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_check_item_templates_module ON check_item_templates (module_type_id);
CREATE INDEX IF NOT EXISTS idx_check_item_templates_criticality ON check_item_templates (criticality_id);
CREATE INDEX IF NOT EXISTS idx_check_item_templates_active ON check_item_templates (is_active);

-- V006: tabelle checkup per sync da device Android (sola lettura sul web)

CREATE TABLE IF NOT EXISTS checkups (
    id                            TEXT PRIMARY KEY,
    client_company_name           TEXT NOT NULL DEFAULT '',
    client_contact_person         TEXT NOT NULL DEFAULT '',
    client_site                   TEXT NOT NULL DEFAULT '',
    client_address                TEXT NOT NULL DEFAULT '',
    client_phone                  TEXT NOT NULL DEFAULT '',
    client_email                  TEXT NOT NULL DEFAULT '',
    island_serial_number          TEXT NOT NULL DEFAULT '',
    island_model                  TEXT NOT NULL DEFAULT '',
    island_installation_date      TEXT NOT NULL DEFAULT '',
    island_last_maintenance_date  TEXT NOT NULL DEFAULT '',
    island_operating_hours        INTEGER NOT NULL DEFAULT 0,
    island_cycle_count            BIGINT NOT NULL DEFAULT 0,
    technician_name               TEXT NOT NULL DEFAULT '',
    technician_company            TEXT NOT NULL DEFAULT '',
    technician_certification      TEXT NOT NULL DEFAULT '',
    technician_phone              TEXT NOT NULL DEFAULT '',
    technician_email              TEXT NOT NULL DEFAULT '',
    checkup_date                  BIGINT NOT NULL,
    header_notes                  TEXT NOT NULL DEFAULT '',
    island_type                   TEXT NOT NULL DEFAULT '',
    island_type_id                TEXT,
    status                        TEXT NOT NULL DEFAULT 'DRAFT',
    created_at                    BIGINT NOT NULL,
    updated_at                    BIGINT NOT NULL,
    completed_at                  BIGINT,
    synced_at                     BIGINT,
    is_deleted                    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_checkups_status     ON checkups (status);
CREATE INDEX IF NOT EXISTS idx_checkups_updated_at ON checkups (updated_at);

CREATE TABLE IF NOT EXISTS checkup_island_associations (
    id               TEXT PRIMARY KEY,
    checkup_id       TEXT NOT NULL,
    island_id        TEXT NOT NULL,
    association_type TEXT NOT NULL,
    notes            TEXT,
    created_at       BIGINT NOT NULL,
    updated_at       BIGINT NOT NULL,
    synced_at        BIGINT,
    CONSTRAINT uq_checkup_island UNIQUE (checkup_id, island_id)
);

CREATE INDEX IF NOT EXISTS idx_cia_checkup ON checkup_island_associations (checkup_id);
CREATE INDEX IF NOT EXISTS idx_cia_island  ON checkup_island_associations (island_id);

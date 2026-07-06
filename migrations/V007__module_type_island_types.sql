-- ============================================================
-- V007: module_type_island_types
--
-- Tabella di associazione many-to-many tra module_types e
-- island_types. Determina quali moduli vengono inclusi nella
-- checklist di un checkup in base al tipo di isola selezionato.
-- Gestita dall'ADMIN e sincronizzata verso tutti i device.
-- ============================================================

CREATE TABLE IF NOT EXISTS module_type_island_types (
    island_type_id TEXT NOT NULL,
    module_type_id TEXT NOT NULL,
    PRIMARY KEY (island_type_id, module_type_id)
);

CREATE INDEX IF NOT EXISTS idx_module_type_island_types_module
    ON module_type_island_types (module_type_id);

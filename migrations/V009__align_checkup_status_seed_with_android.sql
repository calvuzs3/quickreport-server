-- ============================================================
-- V009: Allinea il seed di checkup_statuses / checkup_status_transitions
-- ai valori usati dal seed Android (CheckUpStatusDefaults.kt).
--
-- V005 aveva introdotto colori/emoji/sort_order e un grafo di
-- transizioni leggermente diversi da quelli seminati lato client
-- (fresh install / MIGRATION_7_8). Non è un problema funzionale
-- (il pull sovrascrive sempre i default locali con quelli server),
-- ma disallinea la UI nella breve finestra offline pre-sync.
--
-- Aggiorna solo i valori "cosmetici"/di configurazione (colore,
-- emoji, sort_order, blocks_deletion, marks_completion) sulle righe
-- esistenti — id/code restano invariati, nessun impatto sui checkup
-- già salvati con quello status.
-- ============================================================

UPDATE checkup_statuses SET
    color_hex = '#EEEEEE', icon_emoji = '📝', sort_order = 0,
    blocks_deletion = FALSE, marks_completion = FALSE,
    updated_at = (extract(epoch from now()) * 1000)::bigint
WHERE id = 'DRAFT';

UPDATE checkup_statuses SET
    color_hex = '#9E9E9E', icon_emoji = '⏳', sort_order = 1,
    blocks_deletion = FALSE, marks_completion = FALSE,
    updated_at = (extract(epoch from now()) * 1000)::bigint
WHERE id = 'IN_PROGRESS';

UPDATE checkup_statuses SET
    color_hex = '#1976D2', icon_emoji = '✅', sort_order = 2,
    blocks_deletion = TRUE, marks_completion = TRUE,
    updated_at = (extract(epoch from now()) * 1000)::bigint
WHERE id = 'COMPLETED';

UPDATE checkup_statuses SET
    color_hex = '#388E3C', icon_emoji = '📤', sort_order = 3,
    blocks_deletion = TRUE, marks_completion = FALSE,
    updated_at = (extract(epoch from now()) * 1000)::bigint
WHERE id = 'EXPORTED';

UPDATE checkup_statuses SET
    color_hex = '#C49000', icon_emoji = '📦', sort_order = 4,
    blocks_deletion = TRUE, marks_completion = FALSE,
    updated_at = (extract(epoch from now()) * 1000)::bigint
WHERE id = 'ARCHIVED';

-- Sostituisce il grafo di transizioni con l'esatto set Android (6 righe,
-- invece delle 7 di V005 — COMPLETED->IN_PROGRESS e COMPLETED->ARCHIVED
-- rimosse, DRAFT->COMPLETED aggiunta).
DELETE FROM checkup_status_transitions;

INSERT INTO checkup_status_transitions (from_status_id, to_status_id) VALUES
    ('DRAFT',       'IN_PROGRESS'),
    ('DRAFT',       'COMPLETED'),
    ('IN_PROGRESS', 'DRAFT'),
    ('IN_PROGRESS', 'COMPLETED'),
    ('COMPLETED',   'EXPORTED'),
    ('EXPORTED',    'ARCHIVED')
ON CONFLICT DO NOTHING;

# QReport Server — Claude Code context

Ktor server (Kotlin) per il backend di QReport Android.  
Build: `./gradlew buildFatJar` → `build/libs/qreport-server-all.jar`  
Run locale: `./gradlew run` (richiede PostgreSQL + variabili d'ambiente)

## Stack

- Ktor 3 + Netty
- Exposed ORM 0.50 (DSL, no DAO layer)
- PostgreSQL 15 via HikariCP
- JWT HMAC256 (auth0/java-jwt)
- kotlinx.serialization JSON

## Variabili d'ambiente richieste

```
DB_URL=jdbc:postgresql://localhost:5432/qreport_db
DB_USER=qreport_user
DB_PASSWORD=...
JWT_SECRET=...          # almeno 32 caratteri
JWT_ISSUER=qreport-server
JWT_AUDIENCE=qreport-android
```

## File chiave

| File | Ruolo |
|------|-------|
| `src/.../model/SyncDto.kt` | Tutti i DTO (LoginRequest/Response, SyncPayload, entità, UserResponse) |
| `src/.../repository/Tables.kt` | Definizioni Exposed di tutte le tabelle (inclusa `AuthUsers`) |
| `src/.../repository/SyncServerRepository.kt` | pull(), push(), upsert helpers, row mapper |
| `src/.../routes/AuthRoutes.kt` | POST /auth/login — legge `auth_users`, emette JWT con `role` |
| `src/.../routes/SyncRoutes.kt` | GET /sync/pull, POST /sync/push (estrae `role` dal JWT) |
| `src/.../routes/AdminRoutes.kt` | CRUD `/admin/users` — solo ADMIN (JWT) |
| `src/.../plugins/Security.kt` | Configurazione JWT verifier |
| `src/.../plugins/Routing.kt` | Registrazione routes + GET /api/version (pubblico) |
| `migrations/` | SQL da applicare manualmente in ordine progressivo |

## Schema database — tabelle sincronizzate

### Gruppo client management (bidirezionale, tutti gli utenti)
| Tabella | Entità |
|---------|--------|
| `clients` | Clienti |
| `contacts` | Contatti |
| `contracts` | Contratti |
| `facilities` | Stabilimenti |
| `facility_islands` | Isole robotizzate |
| `mechanical_units` | Unità meccaniche |
| `maintenance_logs` | Log manutenzione |
| `island_documents` | Documenti allegati |
| `island_types` | Tipi isola (master, bidirezionale) |

### Gruppo checkup master data (pull = tutti; push = solo ADMIN)
| Tabella | Entità |
|---------|--------|
| `module_types` | Tipi modulo checkup (es. Meccanico, Sicurezza) |
| `criticality_levels` | Livelli criticità (es. Critico, Alto, Medio) |
| `checkup_statuses` | Stati workflow checkup (es. DRAFT, COMPLETED) |
| `checkup_status_transitions` | Transizioni permesse tra stati |
| `check_item_templates` | Template voci checklist |

### Tabelle di sistema
| Tabella | Ruolo |
|---------|-------|
| `auth_users` | Credenziali utenti (username, bcrypt hash, `role`) |
| `sync_log` | Log sessioni sync (device_id, timestamp, contatori) |

## Campi sync su ogni entità sincronizzata

```sql
updated_at  BIGINT NOT NULL          -- aggiornato ad ogni scrittura
synced_at   BIGINT                   -- impostato dopo push confermato
is_deleted  BOOLEAN NOT NULL DEFAULT FALSE  -- soft delete
```

Logica pending: `updated_at > COALESCE(synced_at, 0)`

## Sistema di ruoli

- Il campo `role` in `auth_users` può essere `'ADMIN'` o `'TECHNICIAN'` (default).
- Al login viene incluso come claim `"role"` nel JWT.
- `LoginResponse` restituisce `{ "token": "...", "role": "ADMIN" }`.
- `POST /sync/push` accetta master data checkup solo se il claim JWT `role == "ADMIN"`.
- Il pull è sempre completo per tutti.

## Gestione utenti via REST API (ADMIN)

Tutti gli endpoint richiedono JWT con `role=ADMIN`. Il `password_hash` non è mai restituito.

| Metodo | Endpoint | Descrizione |
|--------|----------|-------------|
| GET | `/admin/users` | Lista utenti (`{ data: [...], total: N }`) |
| GET | `/admin/users/{id}` | Singolo utente |
| POST | `/admin/users` | Crea utente (`username`, `password`, `role`) |
| PUT | `/admin/users/{id}` | Aggiorna `role`, `isActive`, `password` (tutti opzionali) |
| DELETE | `/admin/users/{id}` | Disattiva utente (soft: `is_active = false`) |

## Aggiungere un utente (via SQL — alternativa manuale)

1. Modificare `passwords.txt` (formato `username:password`, non committare)
2. Eseguire `GeneratePassword.kt` da IDE → stampa gli INSERT SQL
3. Eseguire gli INSERT su psql, specificando il role:

```sql
INSERT INTO auth_users (username, password_hash, is_active, role, created_at)
VALUES ('nome', '$2a$10$...hash...', TRUE, 'ADMIN', 1780000000000);
-- oppure role = 'TECHNICIAN' per tecnici normali
```

## Promuovere un utente esistente ad ADMIN

Via API: `PUT /admin/users/{id}` con body `{ "role": "ADMIN" }`.

Via SQL:
```sql
UPDATE auth_users SET role = 'ADMIN' WHERE username = 'nome_utente';
```
Il token esistente NON viene aggiornato — l'utente deve fare logout/login per ottenere
un nuovo token con il claim `role=ADMIN`.

## Migrazioni

Le migrazioni sono SQL plain da applicare in ordine su psql.
Non è presente Flyway — applicare manualmente prima del deploy del codice.

```
V003__island_types.sql
V004__island_types_sync_fields.sql
V005__checkup_master_data.sql   ← aggiunge 4 tabelle checkup + role in auth_users
```

## Pattern upsert (SyncServerRepository)

Ogni entità usa exists-check + insert/update (non `UPSERT ON CONFLICT`):
```kotlin
val exists = Table.selectAll().where { Table.id eq dto.id }.count() > 0
if (exists) { Table.update(...) } else { Table.insert(...) }
```

## Deploy

```bash
# build
./gradlew buildFatJar

# copia sulla VM
scp build/libs/qreport-server-all.jar root@<vm>:/opt/qreport/

# riavvia
systemctl restart qreport
journalctl -u qreport -f
```

# QReport — Sistema di Sincronizzazione Remota

**Versione:** 1.2  
**Data:** Giugno 2026  
**Complementa:** `README.md` (architettura base app)

---

## INDICE

1. [Panoramica](#1-panoramica)
2. [Architettura del sistema](#2-architettura-del-sistema)
3. [Infrastruttura server](#3-infrastruttura-server)
4. [Modello dati](#4-modello-dati)
5. [Meccanismo di sincronizzazione](#5-meccanismo-di-sincronizzazione)
6. [API Reference](#6-api-reference)
7. [Architettura Android — layer sync](#7-architettura-android--layer-sync)
8. [Guida al deploy](#8-guida-al-deploy)
9. [Guida operativa](#9-guida-operativa)
10. [Guida utente](#10-guida-utente)
11. [Risoluzione problemi](#11-risoluzione-problemi)

---

## 1. PANORAMICA

### 1.1 Obiettivo

Il sistema di sincronizzazione permette a più dispositivi Android di condividere e mantenere aggiornato lo stesso dataset (clienti, stabilimenti, contatti, isole, unità meccaniche) attraverso un server centrale.

Ogni dispositivo lavora in modalità **offline-first**: il database Room locale è sempre la sorgente di verità per l'interfaccia utente. La sincronizzazione avviene su richiesta esplicita dell'utente o automaticamente quando l'app torna in foreground.

### 1.2 Scope attuale

Il sistema sincronizza due gruppi di entità con politiche di autorizzazione distinte.

**Gruppo client management** — bidirezionale, tutti gli utenti autenticati:

| Entità            | Tabella Room       | Tabella PostgreSQL |
|-------------------|--------------------|--------------------|
| Clienti           | `clients`          | `clients`          |
| Contatti          | `contacts`         | `contacts`         |
| Contratti         | `contracts`        | `contracts`        |
| Stabilimenti      | `facilities`       | `facilities`       |
| Isole robotizzate | `facility_islands` | `facility_islands` |
| Unità meccaniche  | `mechanical_units` | `mechanical_units` |
| Maintenance Log   | `maintenance_logs` | `maintenance_logs` |
| Documents         | `island_documents` | `island_documents` |
| Tipi isola        | `island_types`     | `island_types`     |

**Gruppo checkup master data** — pull per tutti, push solo ADMIN:

| Entità                  | Tabella Room              | Tabella PostgreSQL          |
|-------------------------|---------------------------|-----------------------------|
| Tipi modulo             | `module_types`            | `module_types`              |
| Livelli criticità       | `criticality_levels`      | `criticality_levels`        |
| Stati checkup           | `checkup_statuses`        | `checkup_statuses`          |
| Transizioni stato       | `checkup_status_transitions` | `checkup_status_transitions` |
| Template voci checklist | `check_item_templates`    | `check_item_templates`      |

Check-up, foto e interventi tecnici non sono sincronizzati (previsti in una fase futura).

### 1.3 Principi di design

- **Offline-first**: l'app funziona completamente senza rete
- **Bidirectional**: ogni device può creare, modificare ed eliminare record
- **Incremental**: vengono trasferiti solo i record modificati dall'ultima sync
- **Last-write-wins**: in caso di conflitto vince il record con `updatedAt` più recente
- **Soft delete**: i record eliminati vengono marcati `isDeleted=true` e propagati
- **Role-based push**: i master data checkup possono essere pushati solo da utenti `ADMIN`

---

## 2. ARCHITETTURA DEL SISTEMA

```
┌─────────────────────────────────────────────────────────────────┐
│                        INTERNET                                  │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTPS :443
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   NGINX PROXY MANAGER                            │
│              qreport.calvuz.net → Let's Encrypt SSL             │
│              Forward → x.x.x.x:x                        │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP :x
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    VM                         │
│                     IP: x.x.x.x                            │
│                                                                  │
│  ┌─────────────────────────┐   ┌──────────────────────────────┐ │
│  │   Ktor Server           │   │   PostgreSQL                 │ │
│  │   (systemd service)     │◄──│   qreport_db                 │ │
│  │   /opt/qreport/         │   │   qreport_user               │ │
│  └─────────────────────────┘   └──────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                             ▲
                             │ HTTPS xxxx.xxxx.xxx
              ┌──────────────┴──────────────┐
              │                             │
┌─────────────▼──────────┐   ┌─────────────▼──────────┐
│   Android Device A      │   │   Android Device B      │
│   Room DB (locale)      │   │   Room DB (locale)      │
│   Tecnico 1             │   │   Tecnico 2             │
└─────────────────────────┘   └─────────────────────────┘
```

### 2.1 DNS e rete
x

---

## 3. INFRASTRUTTURA SERVER

### 3.1 Stack tecnologico

| Componente | Tecnologia | Versione |
|------------|-----------|---------|
| Linguaggio | Kotlin | 2.x |
| Framework HTTP | Ktor + Netty | 3.x |
| ORM | Exposed | 0.50.x |
| Database | PostgreSQL | 15.x |
| Connection pool | HikariCP | 5.x |
| Autenticazione | JWT (HMAC256) | — |
| Password hashing | BCrypt | — |
| Reverse proxy | Nginx Proxy Manager | — |
| Process manager | systemd | — |
| OS | Debian 12 | — |

### 3.2 Struttura progetto server

```
qreport-server/
├── src/main/kotlin/net/calvuz/qreport/
│   ├── Application.kt              ← entry point, avvia embeddedServer
│   ├── database/
│   │   └── DatabaseFactory.kt      ← HikariCP connection pool
│   ├── model/
│   │   └── SyncDto.kt              ← DTO condivisi (LoginRequest/Response, SyncPayload, entità)
│   ├── repository/
│   │   └── SyncServerRepository.kt ← query SQL con Exposed, upsert, pull
│   ├── routes/
│   │   ├── AuthRoutes.kt           ← POST /auth/login
│   │   └── SyncRoutes.kt           ← GET /sync/pull, POST /sync/push
│   └── plugins/
│       ├── Security.kt             ← JWT configuration
│       ├── Serialization.kt        ← kotlinx.serialization JSON
│       └── Routing.kt              ← registrazione routes
├── tools/
│   └── GeneratePassword.kt         ← utility BCrypt per creare utenti
├── passwords.txt                   ← credenziali (NON committare su Git)
├── application.yaml                ← configurazione porta
└── build.gradle.kts
```

### 3.3 Variabili d'ambiente

Il server legge la configurazione da variabili d'ambiente definite in `/opt/qreport/.env`:

```bash
DB_URL=jdbc:postgresql://localhost:5432/qreport_db
DB_USER=qreport_user
DB_PASSWORD=<password_db>
JWT_SECRET=<stringa_casuale_lunga_almeno_32_caratteri>
JWT_ISSUER=qreport-server
JWT_AUDIENCE=qreport-android
PORT=0                    # opzionale, default x
```

### 3.4 Service systemd

Il server è gestito da systemd come servizio che parte automaticamente al boot:

```ini
# /etc/systemd/system/qreport.service
[Unit]
Description=QReport Server
After=network.target postgresql.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/qreport
EnvironmentFile=/opt/qreport/.env
ExecStart=java -jar /opt/qreport/qreport-server-all.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Comandi utili:
```bash
systemctl status qreport       # stato
systemctl restart qreport      # riavvio
journalctl -u qreport -f       # log in tempo reale
journalctl -u qreport -n 100   # ultimi 100 log
```

---

## 4. MODELLO DATI

### 4.1 Campi di sincronizzazione

Ogni entità sincronizzata ha tre campi aggiuntivi rispetto al modello base:

| Campo | Tipo | Descrizione |
|-------|------|-------------|
| `updated_at` | `BIGINT` (epoch ms) | Aggiornato ad ogni scrittura locale (crea/modifica/elimina). Usato per calcolare il delta. |
| `synced_at` | `BIGINT` nullable | Impostato al valore di `updated_at` dopo un push riuscito. `NULL` = mai sincronizzato. |
| `is_deleted` | `BOOLEAN` | Flag soft-delete. Il record viene nascosto nell'UI ma trasmesso al server per propagare l'eliminazione. |

### 4.2 Logica di rilevamento modifiche

Un record viene incluso nel prossimo push se:

```sql
updated_at > COALESCE(synced_at, 0)
```

Questo include automaticamente:
- Record nuovi (`synced_at IS NULL`)
- Record modificati (`updated_at` aggiornato dopo l'ultima sync)
- Record eliminati con soft-delete (`is_deleted=true`, `updated_at` aggiornato)

### 4.3 Schema PostgreSQL completo

```sql
-- clients
CREATE TABLE clients (
    id TEXT PRIMARY KEY,
    company_name TEXT NOT NULL,
    notes TEXT,
    headquarters_json TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    synced_at BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- contacts
CREATE TABLE contacts (
    id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    first_name TEXT NOT NULL,
    last_name TEXT,
    title TEXT,
    role TEXT,
    department TEXT,
    phone TEXT,
    mobile_phone TEXT,
    email TEXT,
    alternative_email TEXT,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_contact_method TEXT,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    synced_at BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- contracts
CREATE TABLE contracts (
    id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    name TEXT,
    description TEXT,
    start_date BIGINT NOT NULL,
    end_date BIGINT NOT NULL,
    has_priority BOOLEAN NOT NULL DEFAULT TRUE,
    has_remote_assistance BOOLEAN NOT NULL DEFAULT TRUE,
    has_maintenance BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    synced_at BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- facilities
CREATE TABLE facilities (
    id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    name TEXT NOT NULL,
    code TEXT,
    notes TEXT,
    facility_type TEXT NOT NULL,
    address_json TEXT,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    synced_at BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- facility_islands
CREATE TABLE facility_islands (
    id TEXT PRIMARY KEY,
    facility_id TEXT NOT NULL,
    commissioning_number TEXT,
    island_type TEXT NOT NULL,
    serial_number TEXT NOT NULL,
    model_number TEXT,
    model TEXT,
    installation_date BIGINT,
    warranty_expiration BIGINT,
    operating_hours BIGINT NOT NULL DEFAULT 0,
    cycle_count BIGINT NOT NULL DEFAULT 0,
    last_maintenance_date BIGINT,
    next_scheduled_maintenance BIGINT,
    custom_name TEXT,
    location TEXT,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    synced_at BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- mechanical_units
CREATE TABLE mechanical_units (
    id TEXT PRIMARY KEY,
    island_id TEXT NOT NULL,
    unit_type TEXT,
    name TEXT NOT NULL,
    serial_number TEXT,
    model TEXT,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    synced_at BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE maintenance_logs (
    id TEXT PRIMARY KEY,
    island_id TEXT NOT NULL,
    operation_type TEXT NOT NULL,
    custom_operation_label TEXT,
    mechanical_unit_id TEXT,
    component_label TEXT,
    description TEXT NOT NULL,
    technician_name TEXT NOT NULL,
    technician_company TEXT,
    operating_hours_at_event INTEGER,
    cycle_count_at_event BIGINT,
    outcome TEXT NOT NULL,
    duration_minutes INTEGER,
    notes TEXT,
    performed_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    synced_at BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE island_documents (
    id              TEXT NOT NULL PRIMARY KEY,
    scope           TEXT NOT NULL,
    island_id       TEXT,
    facility_id     TEXT,
    client_id       TEXT,
    file_name       TEXT NOT NULL,
    file_path       TEXT NOT NULL DEFAULT '',
    file_size       BIGINT NOT NULL DEFAULT 0,
    mime_type       TEXT NOT NULL DEFAULT 'application/octet-stream',
    file_hash       TEXT,
    storage_backend TEXT NOT NULL DEFAULT 'local',
    title           TEXT NOT NULL,
    category        TEXT NOT NULL,
    notes           TEXT,
    created_at      BIGINT NOT NULL,
    updated_at      BIGINT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    synced_at       BIGINT
);

-- sync_log (traccia le sessioni di sync)
CREATE TABLE sync_log (
    id SERIAL PRIMARY KEY,
    device_id TEXT NOT NULL,
    synced_at BIGINT NOT NULL,
    records_pushed INTEGER NOT NULL DEFAULT 0,
    records_pulled INTEGER NOT NULL DEFAULT 0
);

-- auth_users
CREATE TABLE auth_users (
    id SERIAL PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    role TEXT NOT NULL DEFAULT 'TECHNICIAN',   -- 'ADMIN' | 'TECHNICIAN'
    created_at BIGINT NOT NULL
);

-- module_types
CREATE TABLE module_types (
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

-- criticality_levels
CREATE TABLE criticality_levels (
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

-- checkup_statuses
CREATE TABLE checkup_statuses (
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

-- checkup_status_transitions (workflow graph)
CREATE TABLE checkup_status_transitions (
    from_status_id TEXT NOT NULL,
    to_status_id   TEXT NOT NULL,
    PRIMARY KEY (from_status_id, to_status_id)
);

-- check_item_templates
CREATE TABLE check_item_templates (
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
```

---

## 5. MECCANISMO DI SINCRONIZZAZIONE

### 5.1 Flusso completo

```
DEVICE                              SERVER
  │                                   │
  │  1. Calcola delta locale           │
  │     (updated_at > synced_at)       │
  │                                   │
  │  2. POST /sync/push?since=T        │
  │     Body: SyncPayload              │
  │────────────────────────────────►  │
  │                                   │  3. Upsert record ricevuti
  │                                   │  4. Pull record cambiati dal server
  │                                   │     da timestamp T
  │  5. Response: SyncResponse         │
  │     acceptedIds + pulledPayload    │
  │◄────────────────────────────────  │
  │                                   │
  │  6. Applica pulledPayload a Room   │
  │  7. Marca acceptedIds come synced  │
  │  8. Salva lastSyncTimestamp        │
```

### 5.2 Conflict resolution

La strategia adottata è **last-write-wins** basata su `updated_at`:

- Sul server viene usato `OnConflictStrategy.REPLACE` di Exposed
- Sul device viene usato `OnConflictStrategy.REPLACE` di Room
- Il record con `updated_at` più alto sovrascrive quello più vecchio

Questa strategia è sufficiente per il caso d'uso reale: ogni isola robotizzata è seguita tipicamente da un solo tecnico, le modifiche concorrenti sullo stesso record sono rare.

### 5.3 Soft delete

Le eliminazioni non usano `DELETE` SQL. Il flusso è:

1. L'utente elimina un record nell'app
2. Il repository imposta `isDeleted=true` e aggiorna `updatedAt`
3. Al push successivo il record viene inviato al server con `isDeleted=true`
4. Il server lo propaga agli altri device nel prossimo pull
5. I device riceventi lo scrivono con `isDeleted=true` via upsert
6. Tutte le query Room hanno il filtro `WHERE is_deleted = 0`

### 5.4 Trigger della sincronizzazione

| Evento | Comportamento |
|--------|--------------|
| Tap "Sincronizza ora" | Sync incrementale (`since = lastSyncTimestamp`) |
| Tap "Sync completa" | Reset timestamp a 0 + sync completa (scarica tutto) |
| Login riuscito | Reset timestamp a 0 + sync completa automatica |
| App in foreground | Sync automatica se `REMOTE_ENABLED`, loggato, e passati ≥30 min dall'ultima sync |

### 5.5 Token JWT

I token JWT hanno durata di **30 giorni**. Alla scadenza il server risponde con `401 Unauthorized`. Il `TokenExpiryInterceptor` OkHttp intercetta il 401, cancella il token salvato e imposta `isLoggedIn=false` nell'UI, forzando il re-login al prossimo tentativo di sync.

---

## 6. API REFERENCE

Base URL: `https://qreport.calvuz.net`

Tutti gli endpoint tranne `/auth/login` e `/api/version` richiedono il header:
```
Authorization: Bearer <JWT_TOKEN>
```

### GET /api/version

Endpoint pubblico (nessuna autenticazione). Restituisce la versione del server.

**Response 200:**
```json
{
  "name": "qreport-server",
  "version": "1.4.0"
}
```

---

### POST /auth/login

Autentica un utente e restituisce un token JWT.

**Request:**
```json
{
  "username": "tecnico1",
  "password": "password_in_chiaro"
}
```

**Response 200:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "role": "ADMIN"
}
```

Il campo `role` può essere `"ADMIN"` o `"TECHNICIAN"`. Il client Android lo salva in `TokenStorage` e lo usa per mostrare/nascondere i controlli di push dei master data.

**Response 401:** Credenziali non valide.

---

### POST /sync/push?since=\<timestamp\>

Invia i record modificati localmente e riceve in risposta i record cambiati sul server dall'ultimo sync.

**Query parameter:** `since` — epoch millis dell'ultima sync. Usare `0` per scaricare tutto.

**Request body:** `SyncPayload`
```json
{
  "deviceId": "uuid-del-device",
  "syncTimestamp": 1780138969621,
  "clients": [...],
  "contacts": [...],
  "contracts": [...],
  "facilities": [...],
  "facilityIslands": [...],
  "mechanicalUnits": [...],
  "moduleTypes": [...],
  "criticalityLevels": [...],
  "checkupStatuses": [...],
  "checkItemTemplates": [...]
}
```

Le liste `moduleTypes`, `criticalityLevels`, `checkupStatuses`, `checkItemTemplates` vengono elaborate dal server **solo se il JWT del chiamante ha `role = "ADMIN"`**. Se il role è `"TECHNICIAN"` vengono silenziosamente ignorate.

**Response 200:** `SyncResponse`
```json
{
  "acceptedIds": ["id1", "id2", "..."],
  "pulledPayload": {
    "deviceId": "server",
    "syncTimestamp": 1780138970000,
    "clients": [...],
    "contacts": [...],
    ...
  }
}
```

**Response 400:** Errore deserializzazione payload.  
**Response 401:** Token scaduto o non valido.  
**Response 500:** Errore interno del server (vedere log).

---

### GET /sync/pull?since=\<timestamp\>

Scarica tutti i record modificati sul server dopo `since`.

**Query parameter:** `since` — epoch millis. Usare `0` per tutto.

**Response 200:** `SyncPayload` (stesso formato del body di push).

---

### GET /admin/users

Lista tutti gli utenti. Richiede JWT con `role = "ADMIN"`.

**Response 200:**
```json
{
  "data": [
    { "id": 1, "username": "admin", "role": "ADMIN", "isActive": true, "createdAt": 1780000000000 },
    { "id": 2, "username": "tecnico1", "role": "TECHNICIAN", "isActive": true, "createdAt": 1780000000001 }
  ],
  "total": 2
}
```

**Response 403:** JWT valido ma `role != "ADMIN"`.

---

### GET /admin/users/{id}

Singolo utente per id numerico.

**Response 200:** oggetto `UserResponse` (come sopra, singolo elemento).  
**Response 404:** utente non trovato.

---

### POST /admin/users

Crea un nuovo utente.

**Request:**
```json
{
  "username": "nuovo_tecnico",
  "password": "password_in_chiaro",
  "role": "TECHNICIAN"
}
```
`role` accetta `"ADMIN"` o `"TECHNICIAN"` (default). Qualsiasi altro valore viene normalizzato a `"TECHNICIAN"`.

**Response 201:** `UserResponse` del nuovo utente (senza hash password).  
**Response 400:** `username` o `password` vuoti.  
**Response 409:** username già esistente.

---

### PUT /admin/users/{id}

Aggiorna uno o più campi di un utente. Tutti i campi sono opzionali.

**Request:**
```json
{
  "role": "ADMIN",
  "isActive": true,
  "password": "nuova_password"
}
```

**Response 200:** `UserResponse` aggiornato.  
**Response 404:** utente non trovato.

---

### DELETE /admin/users/{id}

Disattiva l'utente (soft-delete: imposta `is_active = false`). Il token JWT esistente rimane valido fino alla scadenza naturale.

**Response 204:** operazione riuscita.  
**Response 404:** utente non trovato.

---

## 7. ARCHITETTURA ANDROID — LAYER SYNC

### 7.1 Package structure

```
sync/
├── app/
│   ├── SyncEventBus.kt             ← SharedFlow per eventi login/logout
│   └── SyncForegroundObserver.kt   ← DefaultLifecycleObserver per sync automatica
├── data/
│   ├── local/
│   │   ├── dao/
│   │   │   └── SyncDao.kt          ← query pending, upsert, mark synced
│   │   ├── SyncSettingsDataStore.kt ← DataStore: mode, lastSync, deviceId, serverUrl
│   │   └── TokenStorage.kt         ← EncryptedSharedPreferences per JWT
│   ├── remote/
│   │   ├── dto/
│   │   │   └── RemoteDtos.kt       ← mirror dei DTO server
│   │   ├── DynamicUrlInterceptor.kt ← OkHttp: riscrive URL per ogni request
│   │   ├── QReportApi.kt           ← Retrofit interface
│   │   ├── RemoteDataSource.kt     ← interfaccia
│   │   ├── RetrofitRemoteDataSource.kt ← implementazione
│   │   ├── ServerUrlHolder.kt      ← holder in-memory URL server
│   │   └── TokenExpiryInterceptor.kt ← OkHttp: pulisce token su 401
│   └── repository/
│       └── SyncRepositoryImpl.kt
├── di/
│   └── NetworkModule.kt            ← Hilt: Retrofit, OkHttp, bindings
├── domain/
│   ├── model/
│   │   ├── SyncMode.kt             ← LOCAL_ONLY | REMOTE_ENABLED
│   │   ├── SyncResult.kt           ← risultato sessione sync
│   │   └── SyncStatus.kt           ← snapshot stato per UI
│   ├── repository/
│   │   └── SyncRepository.kt       ← interfaccia
│   └── usecase/
│       ├── LoginUseCase.kt         ← autentica e salva token
│       └── SyncUseCase.kt          ← orchestra push + pull
├── mapper/
│   └── SyncMapper.kt               ← entity ↔ DTO
└── presentation/
    └── ui/
        ├── SyncLoginScreen.kt
        ├── SyncLoginViewModel.kt
        ├── SyncSettingsScreen.kt
        └── SyncSettingsViewModel.kt
```

### 7.2 Flusso dati Android

```
SyncSettingsScreen
       │ chiama
       ▼
SyncSettingsViewModel
       │ usa
       ▼
SyncUseCase
       │ usa
       ├── SyncDao (Room) ────────► DB locale
       ├── RemoteDataSource ──────► Retrofit ──► Server
       ├── SyncSettingsDataStore ─► DataStore
       └── SyncMapper ────────────► entity ↔ DTO
```

### 7.3 Dipendenze Android da aggiungere

```kotlin
// Retrofit
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// EncryptedSharedPreferences
implementation("androidx.security:security-crypto:1.1.0-alpha06")

// ProcessLifecycleOwner
implementation("androidx.lifecycle:lifecycle-process:2.8.7")
```

### 7.4 Permessi Android

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 8. GUIDA AL DEPLOY

### 8.1 Prima installazione server

**Prerequisiti sulla VM Debian 12:**
```bash
apt update && apt upgrade -y
apt install -y postgresql postgresql-contrib openssh-server

# Java 21
curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
  | gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg
echo "deb https://packages.adoptium.net/artifactory/deb bookworm main" \
  > /etc/apt/sources.list.d/adoptium.list
apt update && apt install -y temurin-21-jdk
```

**Setup PostgreSQL:**
```bash
su - postgres
createuser --pwprompt qreport_user
createdb --owner=qreport_user qreport_db
exit
```

**Creare le tabelle:** eseguire lo schema SQL della sezione 4.3, oppure applicare le migrazioni in ordine:
```bash
psql -U qreport_user -d qreport_db -h localhost -f migrations/V003__island_types.sql
psql -U qreport_user -d qreport_db -h localhost -f migrations/V004__island_types_sync_fields.sql
psql -U qreport_user -d qreport_db -h localhost -f migrations/V005__checkup_master_data.sql
```

**Creare la cartella applicazione:**
```bash
mkdir -p /opt/qreport
nano /opt/qreport/.env   # inserire le variabili d'ambiente
```

**Installare il service:**
```bash
nano /etc/systemd/system/qreport.service   # inserire il contenuto della sezione 3.4
systemctl daemon-reload
systemctl enable qreport
```

### 8.2 Aggiornamento server (nuova versione)

```powershell
# 1. Build nuovo JAR su Windows
.\gradlew.bat buildFatJar

# 2. Copia sulla VM
scp .\build\libs\qreport-server-all.jar root@192.168.0.191:/opt/qreport/
```

```bash
# 3. Riavvia il servizio sulla VM
systemctl restart qreport
systemctl status qreport
```

### 8.3 Nginx Proxy Manager

Nel pannello NPM aggiungere un Proxy Host:

| Campo | Valore |
|-------|--------|
| Domain Names | `qreport.calvuz.net` |
| Scheme | `http` |
| Forward Hostname | `192.168.0.191` |
| Forward Port | `8080` |
| SSL Certificate | Let's Encrypt (auto) |
| Force SSL | ✅ |

Il router deve fare port forward di **443** e **80** verso l'IP di NPM.

---

## 9. GUIDA OPERATIVA

### 9.1 Aggiungere un nuovo utente

**Metodo 1 — REST API (consigliato, richiede token ADMIN):**

```bash
curl -X POST https://qreport.calvuz.net/admin/users \
  -H "Authorization: Bearer <TOKEN_ADMIN>" \
  -H "Content-Type: application/json" \
  -d '{"username":"nuovo_tecnico","password":"password_sicura","role":"TECHNICIAN"}'
```

Per promuovere ad ADMIN:
```bash
curl -X PUT https://qreport.calvuz.net/admin/users/{id} \
  -H "Authorization: Bearer <TOKEN_ADMIN>" \
  -H "Content-Type: application/json" \
  -d '{"role":"ADMIN"}'
```

**Metodo 2 — SQL diretto sulla VM:**

```bash
# 1. Modificare passwords.txt (formato username:password, non committare)
# 2. Eseguire GeneratePassword.kt da IntelliJ → stampa gli INSERT SQL
# 3. Eseguire su psql:
psql -U qreport_user -d qreport_db -h localhost
```

```sql
INSERT INTO auth_users (username, password_hash, is_active, role, created_at)
VALUES ('nuovo_tecnico', '$2a$10$...hash...', TRUE, 'TECHNICIAN', 1780000000000);
```

**Nota:** il token JWT esistente non viene aggiornato dopo una modifica di ruolo. L'utente deve fare logout e login per ricevere un token con il nuovo claim `role`.

Per verificare i ruoli assegnati:
```sql
SELECT username, role, is_active FROM auth_users ORDER BY username;
```

### 9.2 Disabilitare un utente

**Via REST API:**
```bash
curl -X DELETE https://qreport.calvuz.net/admin/users/{id} \
  -H "Authorization: Bearer <TOKEN_ADMIN>"
```

**Via SQL:**
```sql
UPDATE auth_users SET is_active = FALSE WHERE username = 'tecnico_da_disabilitare';
```

Il token esistente rimane valido fino alla scadenza (30 giorni). Per revoca immediata cambiare `JWT_SECRET` in `/opt/qreport/.env` e riavviare il servizio — tutti i token esistenti diventano invalidi.

### 9.3 Verificare lo stato del database

```bash
psql -U qreport_user -d qreport_db -h localhost
```

```sql
-- Conteggi per tabella (client management)
SELECT 'clients' as tabella, COUNT(*) FROM clients WHERE is_deleted = FALSE
UNION ALL
SELECT 'contacts', COUNT(*) FROM contacts WHERE is_deleted = FALSE
UNION ALL
SELECT 'facilities', COUNT(*) FROM facilities WHERE is_deleted = FALSE
UNION ALL
SELECT 'facility_islands', COUNT(*) FROM facility_islands WHERE is_deleted = FALSE
UNION ALL
SELECT 'mechanical_units', COUNT(*) FROM mechanical_units WHERE is_deleted = FALSE;

-- Conteggi master data checkup
SELECT 'module_types' as tabella, COUNT(*) FROM module_types WHERE is_deleted = FALSE
UNION ALL
SELECT 'criticality_levels', COUNT(*) FROM criticality_levels WHERE is_deleted = FALSE
UNION ALL
SELECT 'checkup_statuses', COUNT(*) FROM checkup_statuses WHERE is_deleted = FALSE
UNION ALL
SELECT 'check_item_templates', COUNT(*) FROM check_item_templates WHERE is_deleted = FALSE;

-- Ultime sessioni di sync
SELECT device_id, to_timestamp(synced_at/1000) as sync_time,
       records_pushed, records_pulled
FROM sync_log
ORDER BY synced_at DESC
LIMIT 20;
```

### 9.4 Backup PostgreSQL

```bash
# Backup completo
pg_dump -U qreport_user qreport_db > /opt/qreport/backup_$(date +%Y%m%d).sql

# Restore
psql -U qreport_user qreport_db < /opt/qreport/backup_20260530.sql
```

### 9.5 Monitoraggio

```bash
# Log server in tempo reale
journalctl -u qreport -f

# Uso risorse
systemctl status qreport
df -h                    # spazio disco
free -h                  # memoria

# Connessioni PostgreSQL attive
psql -U qreport_user -d qreport_db -c "SELECT count(*) FROM pg_stat_activity;"
```

---

## 10. GUIDA UTENTE

### 10.1 Configurazione iniziale

1. Aprire l'app QReport
2. Andare su **Impostazioni → Sincronizzazione**
3. Attivare l'interruttore **Sincronizzazione remota**
4. Inserire l'indirizzo server: `https://xxxx.xxxx.xxx` e toccare il tasto di salvataggio (icona spunta)
5. Toccare **Accedi al server**
6. Inserire username e password forniti dall'amministratore
7. Toccare **Accedi**

Dopo il login, la sincronizzazione parte automaticamente e scarica tutti i dati presenti sul server.

### 10.2 Sincronizzazione manuale

Dalla schermata **Impostazioni → Sincronizzazione**, toccare il pulsante **Sincronizza ora**. Durante la sincronizzazione il pulsante mostra un indicatore di caricamento. Al termine compare un messaggio con il numero di record inviati e ricevuti.

### 10.3 Sync completa

Il pulsante **Sync completa (scarica tutto)** azzera il timestamp di ultima sincronizzazione e scarica nuovamente l'intero dataset dal server. Usare quando:
- Si installa l'app su un nuovo dispositivo
- Si sospetta che i dati locali siano incompleti
- Dopo una reinstallazione dell'app

### 10.4 Sincronizzazione automatica

Quando la sincronizzazione remota è attiva e si è loggati, l'app sincronizza automaticamente ogni volta che torna in primo piano, a condizione che siano passati almeno 30 minuti dall'ultima sync. Non è necessaria alcuna azione da parte dell'utente.

### 10.5 Stato sincronizzazione

La schermata **Impostazioni → Sincronizzazione** mostra:

| Informazione | Descrizione |
|-------------|-------------|
| Ultima sincronizzazione | Data e ora dell'ultimo sync riuscito |
| Modifiche in attesa | Numero di record locali non ancora inviati al server |
| ID Dispositivo | Identificatore univoco del dispositivo (utile per il supporto) |

### 10.6 Disconnessione

Toccare **Disconnetti** per uscire dall'account server. I dati locali non vengono cancellati. Per sincronizzare nuovamente sarà necessario fare login.

### 10.7 Errori comuni

| Messaggio | Causa | Soluzione |
|-----------|-------|-----------|
| "Nessuna connessione di rete" | Il dispositivo non ha accesso a internet | Verificare la connessione WiFi o dati mobili |
| "Sessione scaduta, effettua nuovamente il login" | Il token JWT è scaduto (dopo 30 giorni) | Toccare "Accedi al server" e fare login |
| "Errore server" | Problema sul server | Riprovare tra qualche minuto o contattare l'amministratore |
| "Sincronizzazione disabilitata" | La modalità è impostata su "Solo locale" | Attivare l'interruttore "Sincronizzazione remota" |

---

## 11. RISOLUZIONE PROBLEMI

### 11.1 Il server non risponde

```bash
# Verificare che il servizio sia attivo
systemctl status qreport

# Verificare che sia in ascolto sulla porta
ss -tlnp | grep 8080

# Verificare i log per errori
journalctl -u qreport -n 50 --no-pager
```

### 11.2 Errore 500 durante la sync

Controllare i log del server per il dettaglio dell'eccezione:

```bash
journalctl -u qreport -n 100 --no-pager | grep -A 10 "ERROR"
```

Cause frequenti:
- Campo non nullable nel DTO server che riceve `null` dall'app → rendere il campo `nullable` nel `SyncDto.kt` del server
- Colonna mancante nel database → verificare lo schema con `\d <tabella>` in psql

### 11.3 I dati non si sincronizzano

1. Verificare che `lastSyncTimestamp` non sia nel futuro:
   ```sql
   SELECT MAX(synced_at) FROM clients;
   ```
   Se il valore è anomalo, resettare con **Sync completa** dall'app.

2. Verificare che i record abbiano `updated_at` valorizzato:
   ```sql
   SELECT id, updated_at, synced_at FROM clients LIMIT 5;
   ```

### 11.4 PostgreSQL non accetta connessioni esterne

```bash
grep listen_addresses /etc/postgresql/*/main/postgresql.conf
# deve mostrare: listen_addresses = '*'

grep qreport /etc/postgresql/*/main/pg_hba.conf
# deve mostrare la riga host per qreport_user

systemctl restart postgresql
```

### 11.5 Certificato SSL scaduto

Nginx Proxy Manager rinnova automaticamente i certificati Let's Encrypt. Se il certificato è scaduto:
1. Aprire il pannello NPM
2. Andare su **SSL Certificates**
3. Trovare il certificato di `xxxx.xxxx.xxx`
4. Toccare i tre puntini → **Renew**

---

*Documento aggiornato Giugno 2026 — QReport Sync System v1.2*  
*v1.0 Maggio 2026: sistema sync client management*  
*v1.1 Giugno 2026: aggiunto sync checkup master data + sistema di ruoli (V005)*  
*v1.2 Giugno 2026: REST API gestione utenti (/admin/users) + endpoint pubblico /api/version (server 1.4.0)*
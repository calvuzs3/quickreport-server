# QReport Server — CRUD Routes Integration Guide
# Produced alongside qreport-web (Next.js web app)

## Context

The existing Ktor server exposes only three endpoints:
POST /auth/login
POST /sync/push
GET  /sync/pull

The web app requires full REST CRUD access to all six entities.
Two new files must be added to the server project.

---

## Files produced

CrudRepository.kt   — Exposed table definitions + repository interface + implementation
CrudRoutes.kt       — Ktor route handlers for all six entities, protected by jwt-auth

---

## Step 1 — Copy the two files into the server project

CrudRepository.kt  →  src/main/kotlin/net/calvuz/qreport/repository/CrudRepository.kt
CrudRoutes.kt      →  src/main/kotlin/net/calvuz/qreport/routes/CrudRoutes.kt

Both files already declare the correct package at the top:
CrudRepository.kt  →  package net.calvuz.qreport.repository
CrudRoutes.kt      →  package net.calvuz.qreport.routes

---

## Step 2 — Check for duplicate Exposed table objects

CrudRepository.kt declares the six Exposed Table objects:
Clients, Contacts, Contracts, Facilities, FacilityIslands, MechanicalUnits

Open SyncServerRepository.kt and check whether it already defines
any of these objects with the same name.

Case A — SyncServerRepository.kt uses inline anonymous tables or
different object names: no action needed.

Case B — SyncServerRepository.kt defines objects with the same names
(e.g. "object Clients : Table(...)"):
Delete the duplicate declarations from the TOP section of
CrudRepository.kt (lines 10–109) and add an import instead:
import net.calvuz.qreport.repository.<ObjectName>
One import line per object that already exists.

---

## Step 3 — Register the new routes in Application.kt

Open Application.kt (the embeddedServer entry point).

Add the import at the top:
import net.calvuz.qreport.routes.configureCrudRoutes

Instantiate the repository once (before the server block or inside it
as a val, depending on how the existing code is structured):
val crudRepo = ExposedCrudRepository()

Call configureCrudRoutes after the existing plugin/route registrations:
configureSerialization()
configureSecurity()
configureRouting()          // existing sync + auth routes
configureCrudRoutes(crudRepo)   // ADD THIS LINE

The new routes are protected by the same "jwt-auth" JWT authenticator
already configured in Security.kt — no changes needed there.

---

## Step 4 — Verify the authenticator name

CrudRoutes.kt uses:
authenticate("jwt-auth") { ... }

Open Security.kt and confirm the authenticator is registered with
exactly the name "jwt-auth":
install(Authentication) {
jwt("jwt-auth") { ... }
}

If the name differs, update the string in CrudRoutes.kt to match.

---

## Step 5 — Build and deploy

./gradlew buildFatJar

scp build/libs/qreport-server-all.jar \
root@192.168.0.191:/opt/qreport/qreport-server-all.jar

ssh root@192.168.0.191 "systemctl restart qreport && systemctl status qreport"

Verify the new routes are live:
curl -X POST https://qreport.calvuz.net/auth/login \
-H "Content-Type: application/json" \
-d '{"username":"admin","password":"..."}' | jq .token

TOKEN=<value from above>

curl https://qreport.calvuz.net/api/clients \
-H "Authorization: Bearer $TOKEN"
# Expected: {"data":[...],"total":<n>}

---

## Endpoints added

GET    /api/clients
POST   /api/clients
GET    /api/clients/{id}
PUT    /api/clients/{id}
DELETE /api/clients/{id}        (soft delete — sets is_deleted=true)

Same pattern for:
/api/contacts    (supports ?clientId= filter)
/api/contracts   (supports ?clientId= filter)
/api/facilities  (supports ?clientId= filter)
/api/islands     (supports ?facilityId= filter)
/api/mechanical-units  (supports ?islandId= filter)

All endpoints require:
Authorization: Bearer <JWT token>

Response format for list endpoints:
{ "data": [...], "total": <n> }

Response format for single-item endpoints:
{ ...entity fields... }

DELETE returns HTTP 204 No Content.
POST   returns HTTP 201 Created + entity body.
PUT    returns HTTP 200 OK  + entity body.

---

## Notes on soft delete

DELETE does NOT issue a SQL DELETE.
It sets is_deleted=true and updates updated_at on the record.
This keeps the existing sync mechanism intact: the next Android
sync will receive the deleted record and propagate it to all devices.
# ShambaNexa

Multi-farm management for cattle, goats, sheep, poultry, pigs, and fish. The Angular app reads dashboard figures from the Spring Boot API, and PostgreSQL is the system of record.

Brand colors: green `#2E7D32`, gold `#C19A3A`, cream `#FFFBF0`, sidebar `#1A2E1A`.

## Run locally

Java 17, Node.js, and PostgreSQL 16 or 17. The API defaults to `localhost:5432`, database `shambanexa_db`, user `postgres`, password `password`. Override with `DB_URL`, `DB_USER`, and `DB_PASSWORD`.

`docker compose up -d` starts PostgreSQL 16 as database `shambanexa`, user `shambanexa`, password `shambanexa`. Use that database with:

```bash
cd backend && DB_URL=jdbc:postgresql://localhost:5432/shambanexa DB_USER=shambanexa DB_PASSWORD=shambanexa mvn spring-boot:run
```

Otherwise start the API with its defaults:

```bash
cd backend && mvn spring-boot:run
cd frontend && npm install && npm start
```

The API listens on port 8081 so it does not collide with another local Spring app on 8080. The Angular dev server proxies `/api` to that port.

Open http://localhost:4200.

| Account | Password | Role | Scope |
| --- | --- | --- | --- |
| admin@shambanexa.com | Admin@123 | Super Admin | All farms, including Users & Roles |
| manager@shambanexa.com | Manager@123 | Farm Manager | Northern Farm. Updates livestock, stock, staff, and farm records |
| adviser@shambanexa.com | Adviser@123 | Farm Adviser | Central Farm. Publishes advice. Does not change operational records |

Flyway applies schema migrations, then the demo portfolio in `backend/src/main/resources/db/demo`. Start with `--spring.profiles.active=prod` to run migrations only.

API docs: http://localhost:8081/swagger-ui.html

## Roles

A person holds one role. Assigning another role replaces it.

- **Super Admin** manages users and can grant Own Farm Only, Selected Farms, or All Farms. The last admin cannot be demoted.
- **Farm Manager** updates livestock, stock, staff, and farm records inside the assigned scope. A manager with All Farms can read every farm and still cannot open Users & Roles.
- **Farm Adviser** posts advice and medication notes that every signed-in user can read, and keeps a public listing. Farm data stays inside the assigned scope. The adviser does not change livestock, stock, or staff records.
- **Auditor** can read the assigned farms and cannot write operational records.

Registration joins the existing workspace as a Farm Manager on Own Farm Only, after the farm location and boundary are drawn.

## What this release covers

- Sign-in, registration, and farm-scoped access enforced in the API and the app
- Farms, facilities, species, tagged animals, herd batches, and a Tanzania map of farm locations
- Disease, sick, death, and cure reports, with open health alerts drawn on the map
- Append-only movement ledger with idempotent registration and transfer
- Dashboard counts, milk, revenue, alerts, and an activity feed computed from stored rows
- Production, fish-cycle estimates, inventory adjustments, staff, and tasks
- Expert advice visible across the workspace, with optional medication name and note
- Farm Adviser directory: map pin, talent search, availability, load from open alerts and tasks, ratings, and recommendations
- Separate Farm Manager operations screens and Farm Adviser overview
- CSV export of the same figures the dashboard shows

Later PRD phases (medicine withdrawal, full finance journals, offline sync, weather) stay out of this release. Jurisdictional compliance is not claimed.
# shambanexa

# Shamba Nexa — competitive gap audit (8 November 2026)

Scope: source-tree and route inspection on `feature/facility-types-redesign`; comparison with public feature documentation for AgriWebb, Farmbrite and AGRIVI. This is **not** a full runtime, security penetration, UX accessibility, or database integration audit. A module/entity existing does not prove that a complete workflow is functional.

## Existing foundations observed
- Farm and facility management; location/boundary data; Tanzania map.
- Tagged livestock, herd batches, animal movements, health cases, treatments and vaccinations.
- Inventory batches, adjustments, receipts, issues, transfers, suppliers.
- Workforce, tasks, attendance and performance.
- Production records, financial entries, analytics, marketplace, advice and medical suppliers.
- Open-Meteo forecasts and NASA POWER climate integration.
- Tenant/farm access controls, role grants, audit events, Flyway migrations and backend unit tests.

## Prioritized gaps and next implementation increments

| Priority | Area | Evidence / current limitation | Next deliverable | Acceptance criteria |
|---|---|---|---|---|
| P0 | Release confidence | No GitHub Actions workflow was present in inspected tree; recent Angular template compilation failure was reported | Automated Angular build and Java unit tests (CI added in this update) | Both jobs run on PRs; merge blocked on passing checks after repository settings are configured |
| P0 | Regression coverage | Few backend unit tests observed; no Angular spec files found in inspected tree | Add smoke/integration tests for auth, farm scope, animal transfer, stock transactions and health reporting | Unauthorized cross-farm requests denied; representative workflows pass on PostgreSQL |
| P0 | Security and operational readiness | Production guards exist, but runtime hardening and backups not verified | Security review, secret scan, backup/restore drill, audit retention policy | Verified restore procedure, scoped permissions, no committed credentials |
| P1 | Animal weight and growth | Animal profile UI explicitly states weight not stored | Weight event model, growth chart, import from scales | Historical measurements per animal; no overwrite of prior events |
| P1 | Breeding and genealogy | Animal profile explicitly states breeding events not stored | Mating, pregnancy, calving, parentage and due-date tracking | Reproductive events and reminders visible per animal/herd |
| P1 | Treatment withdrawal / medicine traceability | README explicitly excludes medicine withdrawal | Link administered doses to medicine batches and withdrawal windows | Prevent premature clearance for sale; record who administered treatment |
| P1 | Grazing and pasture | Farms/maps exist; paddock rotation and feed-on-offer workflow not verified | Paddocks, grazing moves, pasture rest and stocking density | Time-bounded movements, paddock utilization history |
| P1 | Financial controls | FinancialEntry exists; README excludes full finance journals | Cost allocation to farm/animal/herd/crop, budget vs actual, profit per head | Auditable expense and revenue ledger with farm-level margin |
| P1 | Offline operations | README explicitly excludes offline sync | Offline-first field records with conflict resolution | Create and sync records after connectivity returns; no duplicates |
| P2 | Crop planning | Production records exist; complete field/season planning not verified | Crop season calendar, field work orders, input application, harvest traceability | Plan-to-harvest records and cost per field |
| P2 | Reporting | CSV export exists; printable/custom compliance reports not verified | Report templates, filters, scheduled exports | Scope-safe reproducible reports with provenance |
| P2 | Integrations | No verified RFID/scale/IoT ingestion | EID scanner imports and sensor ingestion | Idempotent import with validation and audit trail |
| P2 | UX quality | Recent UI revisions; browser visual regression and accessibility tests not verified | Responsive E2E smoke tests, keyboard and WCAG checks | Key workflows usable at mobile widths and with keyboard |

## Competitive reference
- AgriWebb: https://www.agriwebb.com/feature — animal weights, fertility, grazing, biosecurity, EID and cost reports.
- Farmbrite: https://www.farmbrite.com/features — withdrawal reminders, genealogy, offline records, farm finances, inventory and reporting.
- AGRIVI: https://www.agrivi.com/products/farm-management-software/enterprise-farms — field execution, crop plans, budgets, compliance and cost visibility.

## Suggested delivery sequence
1. Stabilize builds and protect the branch with required CI checks.
2. Add integration tests and verify farm-scope security before expanding transactional workflows.
3. Deliver animal weight history and breeding events as the next coherent livestock feature.
4. Implement treatment withdrawal and medicine batch traceability, with veterinary review.
5. Build paddock/grazing operations and linked cost accounting.
6. Add offline field collection and reporting after event models stabilize.

## Guardrails
- Use PostgreSQL as the source of truth; never replace stored data with mockup numbers.
- Maintain tenant/farm scoping and audit trails for every new endpoint.
- Use migrations for schema changes, tests for business rules, and realistic empty/error/loading states.
- Keep work on the feature branch until reviewed and approved for merging.

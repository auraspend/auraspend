# Glossary

Full decoder ring for AuraSpend. Promote frequently used terms to `CLAUDE.md`.

## Design
| Term | Meaning |
|------|---------|
| Aurora | AuraSpend's design language: deep violet + lavender + aurora teal, Plus Jakarta Sans with tabular figures, hairline borders and tonal depth. The 2026-10-17 terracotta exploration was reverted before the 0.1.6 release and is not shipped |
| Aurora tokens | `ui/designsystem/AuraTokens.kt`: `AuraSpacing`, `AuraMotion`, `AuraGradients`, `AuraType` |
| Hero moment | Any surface using the full Aurora gradient — now `AuraCardStyle.Hero`, one per screen |
| The mark | `AuraMark`: the illustrated clay pot + coin, drawn with Compose `Canvas`; `AuraMarkMedallion` puts it on a tonal wash for empty states/onboarding |
| Glass | Translucent surface style for floating chrome only (`AuraCardStyle.Glass`) |
| Micro-label | Small uppercase `AuraType.metricLabel` caption above a metric |

## Process
| Term | Meaning |
|------|---------|
| Phase / P0–P6 | Rebuild phases defined in `docs/handoffs/0000-master-plan.md` |
| Gate | Objective exit criteria for a phase (builds, tests, benchmarks, screenshots) |
| Handoff | `docs/handoffs/NNNN-name.md` written at the end of each work session |
| Definition of Done (DoD) | Per-screen checklist in `docs/design/aurora.md` |
| Triage inbox | Swipe accept/reject queue for auto-detected SMS (P2) |
| History walk / backfill | The downwards pass over inbox messages older than the newest page (`SmsIngestPlanner`), one page per ingest call and only while the live queue is empty — see D16 |

## Architecture
| Term | Meaning |
|------|---------|
| Boundary | try/catch edge for IO/platform calls (`boundary {}` / `boundaryOrNull {}`) |
| AuraLog | Single logging entry point (`core/AuraLog.kt`) |
| Pipeline | SMS → `SmsIngestor` → queue → classifier → `ClassificationMemory` → transaction |
| Fusion | `AiSignalFusion` — combines regex + LLM + keyword signals with confidence |
| Flavor | `dev` (development, `.dev` id, "AuraSpend Dev" label) / `prod` (the build that ships) |

## Navigation & IA (target, P1)
| Term | Meaning |
|------|---------|
| Activity | Transactions tab (list + detail + edit) |
| Plan hub | Budgets + Subscriptions + Savings goals tab |
| Insights | Analytics + insight feed tab |
| Quick Add | Amount-first bottom sheet opened by the FAB |

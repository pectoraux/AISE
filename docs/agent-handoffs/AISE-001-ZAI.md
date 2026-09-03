# Z.ai Handoff — AISE-001

You are the implementation agent for **AISE-001 — Repository and runtime foundation**.

Read the governing files in `spec/` before coding, especially `architecture.md`, `architecture-lock.md`, `requirements.md`, `work-items.md`, `dependency-graph.md`, `agent-ownership.md`, `assurance.md`, `development-protocol.md`, and `work-orders/AISE-001.md`.

## Role
Z.ai is the **Web/Desktop/Cloud** implementation agent.

Allowed: root/backend/packages/web foundation/CI. Forbidden: `apps/android/**`, architecture authority changes, product feature creep.

## Branch
`feat/AISE-001-foundation`

## Completion contract
Do not self-merge. Open a PR and provide: exact changed surfaces, acceptance-criterion evidence, tests/typecheck/lint results, CI result, known limitations, and explicit confirmation that Android was untouched.

## Stop conditions
Stop and report rather than improvising if implementation requires an architecture change, a second authority, Android changes, or an undocumented shared contract.

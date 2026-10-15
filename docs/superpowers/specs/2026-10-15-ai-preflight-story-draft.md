# AI Workflow Preflight and Story Draft Contract

## Goal

Make AI-driven PMS workflow actions predictable before execution and make story-node draft content discoverable and writable from AI context.

## Requirements

1. Development-item node completion previews must expose structured blocking preconditions for missing node owner, schedule, workflow fields, and unfinished tasks.
2. Existing human-readable `warnings` remain backward compatible; structured preconditions are additive.
3. Every precondition identifies a stable code, a Chinese user-facing message, whether it blocks execution, and the supported command that can resolve it. The precondition may include safe argument hints and details, but must not contain secrets.
4. Story workbench components must expose a `saveContract` in AI workflow context. The contract must identify `development-item.node.field.update`, the current item/node/version, the component key, variant, and allowed writable fields.
5. Context reads remain pure reads. The save contract describes a future write and must not mutate PMS data.
6. Existing CLI and frontend clients must continue to parse previews that omit the new fields.
7. Existing workflow validation remains authoritative at execution time; preview preconditions are advisory and must never bypass server validation.

## Acceptance Criteria

- Previewing `development-item.node.complete` with a missing owner returns `NODE_OWNER_REQUIRED` and suggests `development-item.node.owner.update`.
- Previewing the same command with a missing start or end date returns `NODE_SCHEDULE_REQUIRED` and suggests `development-item.node.schedule.update`.
- Previewing a node with unfinished tasks reports `INCOMPLETE_TASKS` with the unfinished count.
- A story-node-workbench context component includes a save contract with the current story ID, node ID, node version, item type, variant, and writable field names.
- Existing preview and context contract tests remain green.

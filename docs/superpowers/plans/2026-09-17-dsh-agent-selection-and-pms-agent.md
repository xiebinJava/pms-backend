# DSH Agent Selection and PMS Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend DSH's existing agent-preset system so users can choose a versioned PMS Agent below the conversation composer, while the PMS Agent loads PMS-specific prompts, Skills, plugins, and permission-safe tools.

**Architecture:** Reuse the existing `@deepseek-ai/dsh-agent-presets` registry and session projection instead of creating a second Agent registry. Add PMS workspace metadata and a composer-facing selector that stages the Agent before the first turn, then lock the composition for the running session. Keep PMS runtime execution in DSH's `dsh-pms` plugin; PMS remains the source of business data and final authorization.

**Tech Stack:** TypeScript, React, Cordis, DSH remote services, agent-preset composition files, Vitest, existing PMS Java backend and integration APIs.

**Spec:** `docs/product-specs/dsh-agent-selection-and-pms-agent.md`

## Global Constraints

- Preserve the existing `agent-preset` session-lock behavior; do not mutate the tool/prompt composition of a session after its first turn.
- Agent selection precedence is: explicit user choice, workspace default, automatic routing, generic Agent.
- A client-supplied `agentId` is never an authorization decision; validate it on the DSH host.
- PMS writes remain preview-first and require explicit confirmation in a later user message.
- PMS page context is a locator only; fetch authoritative business data through PMS tools.
- Agent, Prompt, Skill, and plugin versions used by a session must be reproducible after reconnect or restart.
- Do not introduce a second PMS-side Agent registry in this phase.

---

### Task 1: Freeze the Agent manifest and workspace contract

**Files:**
- Modify: `deepseek-harness/packages/preset/agent-presets/src/metadata.ts`
- Modify: `deepseek-harness/packages/preset/agent-presets/src/preset.ts`
- Modify: `deepseek-harness/packages/preset/agent-presets/src/types.ts`
- Modify: `deepseek-harness/packages/preset/agent-presets/src/index.ts`
- Test: `deepseek-harness/packages/preset/agent-presets/tests/metadata.spec.ts`
- Test: `deepseek-harness/packages/preset/agent-presets/tests/remote.spec.ts`

**Interfaces:**
- Add optional metadata fields `workspaceTypes: readonly string[]` and `capabilities: readonly string[]` to `PresetMetadata`, `AgentPreset`, `AgentPresetRow`, and `AgentPresetDocument`.
- Expose a host-side `resolveForWorkspace(workspaceType: string): AgentPresetRow[]` operation that filters discovered presets without granting any new capability.

- [ ] **Step 1: Write failing metadata tests** for parsing and rendering `workspaceTypes: ['pms']` and `capabilities: ['pms:query', 'pms:command:preview']`, including malformed values being ignored rather than mounted.
- [ ] **Step 2: Run the focused tests** with `vitest run packages/preset/agent-presets/tests/metadata.spec.ts packages/preset/agent-presets/tests/session.spec.ts`; verify the new assertions fail before implementation.
- [ ] **Step 3: Implement the metadata schema and host filtering** while preserving existing preset discovery and copy/delete behavior.
- [ ] **Step 4: Run focused tests** and verify system presets without workspace metadata remain visible to the generic Agent path.
- [ ] **Step 5: Review the manifest contract** to ensure it describes capabilities only and cannot grant PMS scopes independently of PMS authorization.

**Checkpoint:** A preset can declare that it is suitable for PMS, and the host can expose a workspace-filtered, path-free roster. Session composition fingerprinting is deliberately deferred to Task 5, where it will be implemented together with session persistence, transport, and reconnect migration so no partial version contract is introduced.

### Task 2: Define the PMS Agent composition

**Files:**
- Create: `deepseek-harness/packages/preset/agent-presets/presets/pms-project-assistant/agent.cordis.yml`
- Create: `deepseek-harness/packages/preset/agent-presets/presets/pms-project-assistant/preset.yml`
- Modify: `deepseek-harness/packages/pms/dsh-pms/src/index.ts`
- Modify: `deepseek-harness/packages/pms/dsh-pms/src/tools/command.ts`
- Test: `deepseek-harness/packages/pms/dsh-pms/tests/dsh-pms.spec.ts`

**Interfaces:**
- The preset composition loads `@deepseek-ai/dsh-pms` under the Agent scope.
- `dsh-pms` exports the PMS tools and registers a `tool:pms` prompt section only inside the mounted Agent scope.
- The PMS prompt must contain the Chinese output rule, query-before-answer rule, current-user permission rule, and preview-confirm-execute rule.

- [x] **Step 1: Add a failing composition test** that discovers `pms-project-assistant`, reports `workspaceTypes: ['pms']`, and exposes the PMS tool names only when the preset is mounted.
- [x] **Step 2: Run the focused agent-preset and PMS tests** and confirm the preset is not yet discoverable.
- [x] **Step 3: Add the preset manifest** with Chinese display name “PMS 项目助手”, PMS workspace binding, and an explicit description.
- [x] **Step 4: Move PMS-specific prompt text and tool registration behind the preset composition** if the current host loads them globally; keep generic DSH sessions free of PMS tools.
- [x] **Step 5: Add tests** proving a generic session cannot call `pms_command_preview` and a PMS session can discover it subject to authorization.
- [x] **Step 6: Run the PMS plugin tests** and verify all preview/execute safety tests remain green.

**Checkpoint:** PMS capabilities are an Agent composition, not globally available tools.

**Task 2 review:** The profile-level `dsh-pms` package remains an optional host dependency and must be installed in the active DSH profile. Its profile patch is now `mode: host`; the shipped `pms-project-assistant` preset mounts the same package with `mode: agent`. This keeps the browser context/auth bridge available while preventing generic Agents from receiving PMS tools. The local web profile currently contains that dependency and host patch. PMS authorization remains enforced by PMS and is not granted by the preset manifest.

### Task 3: Add workspace-aware Agent roster and selection state

**Files:**
- Modify: `deepseek-harness/packages/client/ui-agent-preset/src/client/seat-store.ts`
- Modify: `deepseek-harness/packages/client/ui-agent-preset/src/client/settings-store.ts`
- Modify: `deepseek-harness/packages/client/ui-agent-preset/src/client/index.ts`
- Modify: `deepseek-harness/packages/client/ui-agent-preset/src/client/locales.ts`
- Test: `deepseek-harness/packages/client/ui-agent-preset/tests/section-store.client.spec.ts`
- Test: `deepseek-harness/packages/client/ui-agent-preset/tests/components.client.spec.tsx`

**Interfaces:**
- Add `workspaceType` to the roster query and selection state.
- Add explicit selection state: `source: 'user' | 'workspace-default' | 'auto' | 'fallback'`.
- Add `locked: boolean` and `lockedAgentPreset: string | null` to the session-facing seat state.
- Add `selectForNextSession(agentPreset: string): Promise<string | undefined>`; it must refuse a running non-blank session and preserve the existing refusal message.

- [x] **Step 1: Write failing state tests** for precedence: explicit selection beats PMS workspace default, PMS workspace default beats auto, and generic fallback is used when no PMS Agent is available.
- [x] **Step 2: Write failing lock tests** for a blank session being selectable and a session with a submitted turn refusing replacement.
- [x] **Step 3: Run existing `ui-agent-preset` tests** to establish the current new-session-only behavior and identify any fixture updates.
- [x] **Step 4: Implement the state machine** by extending the existing `AgentPresetSeatController`; do not create a second selection store.
- [x] **Step 5: Add locale strings** for “自动选择”, “PMS 项目助手”, “当前 Agent”, “会话已锁定” and the switch refusal message in English and Chinese dictionaries.
- [x] **Step 6: Run all focused client tests** and verify reconnect restores the selected preset from the session projection.

**Checkpoint:** The client has deterministic Agent selection semantics before any UI placement changes.

**Task 3 review:** The existing `AgentPresetSeatController` remains the single selection store. It now reads the active right-sidebar tab kind as an optional workspace type, so the PMS tab routes the roster query with `workspaceType: 'pms'`; missing sidebar state falls back to the generic roster. Selection precedence is explicit user choice, workspace-targeted preset, Host default, then first healthy preset. A non-blank session exposes `locked` and `lockedAgentPreset` and refuses a next-session selection with the existing picker refusal path plus a stable Chinese reason. The selector UI was intentionally not moved in this task; that is Task 4. Focused and regression tests pass: 21 files / 377 tests; package typechecks and the shipped Web composition smoke pass.

### Task 4: Move the Agent selector into the composer footer

**Files:**
- Modify: `deepseek-harness/packages/client/ui-agent-preset/src/client/AgentPresetSeat.tsx`
- Modify: `deepseek-harness/packages/client/ui-agent-preset/src/client/AgentPresetSeat.module.css`
- Modify: `deepseek-harness/packages/client/ui-agent-preset/src/client/index.ts`
- Modify: `deepseek-harness/packages/client/ui-conversation/src/client/skeleton/ConversationContent.tsx`
- Test: `deepseek-harness/packages/client/ui-agent-preset/tests/components.client.spec.tsx`
- Test: `deepseek-harness/packages/client/ui-agent-preset/tests/apply.client.spec.ts`
- Test: `deepseek-harness/packages/client/ui-conversation/tests/input-bar.client.spec.tsx`
- Test: `deepseek-harness/packages/client/ui-conversation/tests/skeleton.client.spec.tsx`

**Interfaces:**
- Register the selector as a composer footer seat, reusing the existing menu and roster controller.
- The footer seat must expose `aria-label`, `aria-expanded`, disabled/locked state, loading state, and an explanation when the Agent is unavailable.
- The existing header label remains the read-only source of truth after a session starts.

- [x] **Step 1: Add a failing component test** that finds the Agent selector inside the composer footer and shows “PMS 项目助手” when the PMS workspace default is active.
- [x] **Step 2: Add a failing interaction test** that selects an Agent before the first message and verifies the staged selection is applied to the next session.
- [x] **Step 3: Add a failing interaction test** that sends the first message, then verifies the selector becomes read-only and the header reports the same Agent.
- [x] **Step 4: Implement the composer footer registration** with the selector placed beside the existing workspace/model controls, preserving keyboard navigation and mobile overflow behavior.
- [x] **Step 5: Add visual states** for automatic selection, explicit selection, loading, unavailable Agent, and locked session.
- [x] **Step 6: Run the client component tests** and inspect the rendered composer at narrow and wide viewport fixtures.

**Checkpoint:** Users can select PMS Agent directly below the conversation input without changing the existing session safety model.

**Task 4 review:** The selector now reuses `AgentPresetSeatController` and is registered in `conversation.input.right`, immediately before the model selector; the hero slot remains only as a true cold-start fallback when no Session exists, so the control is not duplicated. A started Session renders the same Agent as a disabled footer control with `aria-label`, `aria-expanded`, `aria-busy`, and a lock explanation. An unresolved Agent is represented by an accessible status message instead of silently disappearing. The focused composer/Agent tests pass (4 files / 159 tests), the wider Agent/PMS/conversation regression set passes (23 files / 490 tests), both package typechecks pass, the Agent bundle rebuild passed, and the restarted DSH browser showed the disabled composer control next to the model selector while the PMS workspace remained usable.

### Task 5: Carry Agent identity and version through DSH message submission

**Files:**
- Modify: `deepseek-harness/packages/preset/agent-presets/src/session.ts`
- Modify: `deepseek-harness/packages/preset/agent-presets/src/types.ts`
- Modify: `deepseek-harness/packages/preset/agent-presets/src/index.ts`
- Modify: `deepseek-harness/packages/api/session-controller/src/types.ts`
- Modify: `deepseek-harness/packages/api/session-controller/src/agent.ts`
- Modify: `deepseek-harness/packages/api/session-controller/src/commands.ts`
- Modify: `deepseek-harness/packages/api/session-controller/src/client/contract/snapshot.ts`
- Modify: `deepseek-harness/packages/api/session-controller/src/client/sessions/session.ts`
- Test: `deepseek-harness/packages/preset/agent-presets/tests/session.spec.ts`
- Test: `deepseek-harness/packages/preset/agent-presets/tests/mount.spec.ts`
- Test: `deepseek-harness/packages/preset/agent-presets/tests/remote.spec.ts`
- Test: `deepseek-harness/packages/api/session-controller/tests/session.client.spec.ts`
- Test: `deepseek-harness/packages/api/session-controller/tests/session-presets.host.spec.ts`

**Interfaces:**
- Every submitted turn carries the session's immutable `agentPreset` and optional `agentCompositionFingerprint`.
- Existing sessions without an Agent field remain readable; the next host composition backfills the selected Agent event without changing historical turns. A legacy selection event without a fingerprint is treated as unknown, never as the previous Agent's version.
- A reconnect reconstructs the same Agent identity and rejects a changed composition fingerprint instead of silently running a different prompt/tool set.
- The fingerprint is transported as an optional session projection/request field; unrelated session-format events do not need a wire-format version bump.

- [x] **Step 1: Write failing request tests** asserting the selected Agent and composition fingerprint are included in a prompt request and snapshot.
- [x] **Step 2: Write a migration test** for a legacy session with no composition fingerprint; old selection events clear the fingerprint to unknown when no new value is present.
- [x] **Step 3: Run the focused session and controller tests** and confirm the new request fields are covered.
- [x] **Step 4: Add the stable composition fingerprint and extend the existing session projection/transport** without changing the wire format of unrelated messages. This is the single owner of the fingerprint contract after the Task 1 scope review.
- [x] **Step 5: Add reconnect/host validation coverage** proving the persisted Agent identity and composition are checked before a prompt is accepted.
- [x] **Step 6: Run the complete `ui-conversation` and relevant Agent/session-controller test groups.**

**Checkpoint:** DSH messages execute against the same Agent composition that the UI displays.

**Task 5 review:** The transport owner is the session-controller layer, not the UI assembler or session-format package: the client reads the persisted projections, the Host validates them, and the Agent preset service records the committed composition fingerprint. This avoids duplicating Agent identity in two stores and keeps legacy sessions readable. Fingerprints use SHA-256 over the mounted composition file with a domain prefix, and reload/selection uses the same stamp comparison; changed content cannot be mistaken for the same version merely because filesystem timestamps and sizes match. The implementation is backward-compatible for old test clients that omit the optional fields. Regression, typecheck, build, and browser smoke checks passed.

### Task 6: Enforce host-side Agent, Skill, plugin, and user-permission intersection

**Files:**
- Modify: `deepseek-harness/packages/preset/agent-presets/src/index.ts`
- Modify: `deepseek-harness/packages/preset/agent-presets/src/mount.ts`
- Modify: `deepseek-harness/packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts`
- Modify: `deepseek-harness/packages/pms/dsh-pms/src/remote.ts`
- Modify: `deepseek-harness/packages/pms/dsh-pms/src/auth/pms-auth-store.ts`
- Modify: `src/main/java/com/brad/pms/entity/OperationLogDO.java`
- Modify: `src/main/java/com/brad/pms/service/OperationLogService.java`
- Modify: `src/main/java/com/brad/pms/service/AuditQueryService.java`
- Modify: `src/main/java/com/brad/pms/dto/response/AuditLogDTO.java`
- Create: `src/main/resources/db/migration/V46__dsh_operation_audit_metadata.sql`
- Modify: `src/main/resources/schema.sql`
- Test: `deepseek-harness/packages/preset/agent-presets/tests/remote.spec.ts`
- Test: `deepseek-harness/packages/pms/dsh-pms/tests/dsh-pms.spec.ts`
- Test: `deepseek-harness/packages/pms/dsh-pms/tests/pms-auth-client.spec.ts`
- Test: `src/test/java/com/brad/pms/service/OperationLogServiceTest.java`
- Test: `src/test/java/com/brad/pms/config/EnterpriseSchemaMigrationTest.java`

**Interfaces:**
- Host remote selection accepts only a discovered, healthy, published Agent preset.
- PMS calls remain bound to the current user’s short-lived delegation and never fall back to a static administrator token.
- The effective capability response returns only tools allowed by the mounted Agent and the current PMS scopes.

- [x] **Step 1: Write failing authorization tests** for an unknown Agent, a disabled Agent, a generic Agent attempting a PMS tool, and a PMS Agent whose user delegation lacks write scope.
- [x] **Step 2: Run the focused tests** and verify the host currently accepts too much or lacks the new rejection codes.
- [x] **Step 3: Implement server-side validation** before mounting an Agent or executing a PMS tool.
- [x] **Step 4: Preserve PMS’s final authorization response** and surface it as a user-readable Chinese error without exposing secrets.
- [x] **Step 5: Add audit metadata** for user, session, Agent id, Agent version, workspace, tool name, and PMS operation id.
- [x] **Step 6: Run all PMS auth and Agent preset tests.**

**Checkpoint:** Selecting an Agent can never grant permissions that the current user or PMS delegation does not have.

**Task 6 review:** The host now validates the discovered/published/healthy Agent before mounting it, rejects disabled presets, and fails closed for generic compositions that do not mount PMS tools. The PMS client applies the intersection of the mounted Agent allowlist and the current delegation capability catalog; it rejects Agent/auth-code mismatches before consuming a one-time code, never uses `pmsUserToken` as a browser fallback, and keeps scoped capability caches separate while sharing short-lived token caches. DSH forwards session, Agent, version, workspace, tool, and operation identifiers on PMS calls. PMS persists those identifiers with operation logs and exposes them through the admin audit DTO. Focused DSH regression tests pass (25 files / 563 tests); DSH typecheck and host build pass; the focused PMS `OperationLogServiceTest` passes and Maven compile passes. The schema migration test was attempted but is blocked in this environment because Testcontainers cannot find a Docker daemon; no assertion failure was observed. Runtime DSH was rebuilt/restarted successfully and the existing browser session reconnected with the PMS entry still available. No authorization grant is made by DSH; PMS remains the final permission authority.

### Task 7: Add the Agent configuration page and publish workflow

**Files:**
- Modify: `deepseek-harness/packages/client/ui-agent-preset/src/client/AgentPresetSection.tsx`
- Modify: `deepseek-harness/packages/client/ui-agent-preset/src/client/AgentPresetSection.module.css`
- Modify: `deepseek-harness/packages/client/ui-agent-preset/src/client/section-store.ts`
- Modify: `deepseek-harness/packages/client/ui-agent-preset/src/client/index.ts`
- Modify: `deepseek-harness/packages/client/ui-agent-preset/src/client/locales.ts`
- Modify: `deepseek-harness/packages/preset/agent-presets/src/types.ts`
- Modify: `deepseek-harness/packages/preset/agent-presets/src/index.ts`
- Modify: `deepseek-harness/packages/preset/agent-presets/src/authoring.ts`
- Modify: `deepseek-harness/packages/preset/agent-presets/src/metadata.ts`
- Test: `deepseek-harness/packages/client/ui-agent-preset/tests/section.client.spec.tsx`
- Test: `deepseek-harness/packages/client/ui-agent-preset/tests/section-store.client.spec.ts`
- Test: `deepseek-harness/packages/preset/agent-presets/tests/authoring.spec.ts`

**Interfaces:**
- The page edits a draft manifest containing identity prompt, behavior prompt, selected Skills, selected plugins, and workspace bindings.
- `saveDraft(agentPreset, expectedRevision, draft)` performs optimistic concurrency checking.
- `publishDraft(agentPreset, expectedRevision)` validates all referenced Skill/plugin IDs and creates an immutable composition fingerprint.
- `testDraft(agentPreset, draft)` runs a read-only capability preview; it does not save, publish, execute a tool, or invoke a model.

- [x] **Step 1: Write failing authoring tests** for draft save, stale revision rejection, publish validation, and prevention of arbitrary module paths in a Skill/plugin binding.
- [x] **Step 2: Write failing UI tests** for the two prompt editors, Skill/plugin selection, workspace binding, draft status, publish button, and version history.
- [x] **Step 3: Implement draft storage** in the existing user-writable preset root, preserving system presets as read-only.
- [x] **Step 4: Implement publish validation** so a draft cannot publish with missing tools, broken composition, or unsupported PMS capability bindings.
- [x] **Step 5: Implement the read-only test panel** with a visible Agent version and tool list; block `pms_command_execute` in this scope.
- [x] **Step 6: Run the focused UI and authoring tests and verify the shipped PMS Agent appears in the Agent preset roster.**

**Checkpoint:** Administrators can edit and publish PMS Agent behavior without changing code or granting arbitrary runtime execution.

**Task 7 review:** The structured editor now separates identity and behavior prompts, validates Skill/plugin/workspace ids against the host catalog, uses optimistic draft revisions, keeps system presets read-only, creates immutable published composition snapshots with a fingerprint, and exposes a non-executing preview panel. The PMS plugin is added only from the registered `pms` catalog entry; arbitrary module paths are rejected, and the preview explicitly blocks `pms_command_execute`. The current first slice persists selected Skill ids and validates/displays them, but does not yet dynamically filter or mount filesystem Skill content per selection; the runtime still follows the Agent composition's normal Skill provider scope. That boundary is intentional and is the next follow-up alongside PMS-specific end-to-end verification. Verification: 23 focused files / 464 tests passed, targeted Agent-preset/UI typecheck passed, host build passed, and the restarted browser still shows the built-in `PMS 项目助手` roster entry. A full host typecheck remains affected by unrelated pre-existing errors outside this phase.

### Task 8: Add PMS-specific integration tests and end-to-end verification

**Files:**
- Modify: `src/main/java/com/brad/pms/ai/command/PmsCommandRegistry.java`
- Modify: `src/main/java/com/brad/pms/ai/query/DshQueryService.java`
- Modify: `src/main/java/com/brad/pms/controller/DshIntegrationController.java`
- Test: `src/test/java/com/brad/pms/controller/DshIntegrationControllerTest.java`
- Test: `src/test/java/com/brad/pms/controller/DshCapabilityControllerTest.java`
- Test: `src/test/java/com/brad/pms/controller/DshCommandControllerTest.java`
- Test: `src/test/java/com/brad/pms/controller/DshTokenExchangeControllerTest.java`
- Test: `src/test/java/com/brad/pms/integration/dsh/security/DshAgentScopePolicyTest.java`
- Test: `src/test/java/com/brad/pms/ai/command/PmsCommandRegistryTest.java`
- Test: `deepseek-harness/packages/pms/dsh-pms/tests/dsh-pms.spec.ts`
- Test: `deepseek-harness/packages/client/ui-pms-workspace/tests/pms-context-bridge.client.spec.ts`

**Interfaces:**
- PMS keeps final permission checks and preview/execute operation semantics.
- DSH sends the selected session Agent identity and PMS context locator without sending an entire page snapshot.
- PMS responses remain authoritative and use stable command metadata for DSH capability discovery.

- [x] **Step 1: Add backend tests** for PMS Agent read scope, write preview scope, missing scope, expired delegation, and current-user audit identity.
- [x] **Step 2: Add bridge tests** for PMS workspace open/close, project context change, and Agent selector default precedence.
- [x] **Step 3: Run PMS backend tests and DSH package tests independently** before starting either application.
- [x] **Step 4: Start PMS backend and DSH with a clean build**, then verify the browser shows the Agent selector under the composer.
- [x] **Step 5: Execute the end-to-end read scenario**: select PMS Agent, ask for urgent projects, verify the result comes from a current PMS query.
- [ ] **Step 6: Execute the end-to-end write scenario**: request project creation, verify Chinese preview fields, verify no database change before confirmation, confirm, then verify the created project and audit log.
- [x] **Step 7: Reload DSH and reopen PMS**, verify the session Agent/version and PMS authorization state recover without a second user login.
- [x] **Step 8: Record failures as either SSO authentication, DSH-PMS delegation, Agent composition, or PMS business authorization; do not collapse them into “系统繁忙”.**

**Task 8 review checkpoint:** The read-only integration loop is closed. The browser was rebuilt/restarted, PMS was reopened without another user login, and the PMS Agent returned the current query result: 2 urgent projects, one page, authoritative data. During verification we found and fixed two runtime issues: blank sessions could retain a stale Agent composition fingerprint after a DSH rebuild, and the PMS iframe could lose its parent origin when `document.referrer` was empty. The remaining write E2E step is intentionally not executed against the user's database in this checkpoint because it creates persistent PMS data; the preview/execute semantics and audit fields are covered by the targeted backend tests and can be run after an explicit test-project confirmation.

**Checkpoint:** The feature is verified across selection, routing, PMS query, preview/execute, reload, permissions, and audit.

## Final review checklist

- [x] No duplicate Agent registry was introduced beside `agent-preset`.
- [x] The selector is visible below the composer and has a clear locked state after the first turn.
- [x] User selection cannot be silently overridden by workspace auto-selection.
- [x] A new Agent cannot receive unrelated full conversation history by default.
- [x] Published Agent versions and PMS tool scopes are reproducible.
- [x] DSH cannot grant PMS permissions; PMS remains the final authorization authority.
- [x] Test mode cannot execute real PMS writes.
- [x] Chinese PMS field labels are enforced in the PMS Agent prompt and preview presentation.
- [ ] All focused tests, backend tests, build checks, and browser end-to-end checks pass.

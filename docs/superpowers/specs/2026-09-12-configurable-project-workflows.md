# Configurable Project Workflows — Product and Technical Specification

**Status:** Approved for implementation
**Date:** 2026-09-12

## Outcome

Administrators can create multiple sequential workflow templates, visually edit their stages and node-detail fields, preview the result, publish versions, and assign a default template to each project type. Project creation preselects that default but allows an authorized creator to choose another published template. Every project keeps the exact published template version it was created from.

## Product rules

- Workflow template administration lives under Configuration Management.
- A project type is independent from the existing project level (`projectLevel`). Project types and template defaults are administered alongside workflows; seed a general-purpose type/default so existing installations remain usable.
- V1 is a strictly sequential flow. Branches and parallel stages are not supported.
- Published templates are versioned snapshots. Editing an existing template creates/updates a draft; publishing creates an immutable version. Existing projects keep their bound version. New projects use the default for their type unless the creator switches to another published template.
- A project created from a template materializes its ordered project nodes from that version. Existing project node IDs, states, owners, schedules, completion timestamps, and specialized workbench data must not be regenerated or discarded during migration.
- Node name, description, order, and supported workbench/module attachment are configurable. A custom node may have custom fields and the generic fixed blocks.
- Every node detail always presents the fixed node-owner block, schedule block, and task kanban. These are system blocks, not removable custom fields.
- Custom fields: single-line text, multiline text, number, date, single-select, multi-select, person, and attachment. Field requiredness, label, order, and options are configurable. Field permissions inherit node visibility/edit access; per-field ACL is out of scope.
- Project Basic Info is a built-in, configurable node component: it can be shown/hidden and ordered among detail sections, and its displayed/required canonical fields can be configured. Canonical project fields remain canonical fields for existing list/search/report semantics rather than becoming unrelated JSON values.
- Current specialist workbenches remain first-class reusable components and can be attached to one or more template nodes. Component-owned tables/services and completion validation remain intact; the template controls placement and stage order, not the semantics of these specialized modules.
- Admin UI must be visual: connected node cards on a canvas, drag-to-reorder (with keyboard-accessible alternatives), click-to-edit inspector, component and field palette, and a faithful detail preview. It must not require JSON editing.
- Authorized workflow administrators can edit/publish templates; project creators can select only published templates they can use. Respect the existing permission model and audit configuration changes.

## Current workflow compatibility map

The existing nine-stage compatibility flow contains nine ordered stages. Seed one template that reproduces it and attaches these modules:

| Order | Node | Existing module / behavior |
|---|---|---|
| 1 | 项目立项与启动 | Project Basic Info |
| 2 | 需求澄清与范围基线 | Requirement scope and baseline |
| 3 | 方案设计、评审与决策 | Solution package, reviews, decision |
| 4 | 计划、资源与风险基线 | Plan, resource, risk |
| 5 | 开发与迭代 | Iterations, topics, stories |
| 6 | 验收与问题闭环 | Acceptance and defect closure |
| 7 | 发布决策与交接 | Release decision and operations handover |
| 8 | 价值复盘与总结 | Value review and retrospective |
| 9 | 知识沉淀与行动项 | Knowledge assets and actions |

The seed retains the existing stable node keys so project rows and historical module data continue to resolve. Specialized runtime validation is keyed by configured component identity, so a component can move to a custom stable node key. New custom nodes use generated stable keys/IDs and generic field validation. Runtime UI placement is driven by template node/module metadata rather than node-key conditionals.

## Persistence and API contract

- Add forward-only Flyway migration `V42__configurable_workflow_templates.sql` (confirm current latest migration before authoring).
- Persist project types; templates; immutable published template versions; ordered node definitions; node component attachments; custom field definitions/options; project-to-template-version binding; and generic node-field values/attachment metadata.
- Store a template-version snapshot or normalized immutable version-owned records so published data cannot be changed by later draft edits.
- Store `project_type_id` and `workflow_template_version_id` on projects. Project creation accepts both; backend validates that the requested version is published/usable for that type and defaults from the type when omitted.
- Expose permission-protected APIs for project types, template list/detail, draft save, publish, preview, and project creation choices. Keep DTO validation server-side; never trust canvas order, field options, module IDs, or required flags from the browser.
- Attachment field uploads must reuse the existing storage abstraction, enforce allowed size/type rules, and enforce project/node access when reading/deleting. Do not persist raw file bytes in a field JSON blob.
- Existing project rows are bound to the seeded compatibility version and get its type without rewriting their `project_node` rows. Migration must be idempotent under the repository's migration/test conventions and preserve referential integrity.

## Completion and rendering behavior

- For nodes attached to an existing specialized component, retain its current service-owned completion validators, including all data/role rules. Resolve validation by component identity declared on the template node, not by assuming one global node order.
- Generic nodes validate required custom fields before completion.
- Node order and next-node activation follow the project's bound template version. Existing node state and explicit completion action remain authoritative.
- Render the same workbench modules currently available, but choose them from node component metadata. Render Project Basic Info and generic fields from node field/component definitions; keep owner, schedule, and task kanban fixed.
- Unknown/missing module references must fail safely and visibly rather than hiding node detail or bypassing completion validation.

## Acceptance criteria

1. The seeded template reproduces all nine current stages and their existing workbenches.
2. A workflow administrator can visually create/edit/reorder nodes, attach current workbench modules, configure fields and Project Basic Info, preview, save a draft, and publish.
3. A project type can have exactly one default published template; default assignment is validated and new project creation preselects it while permitting a switch.
4. A project remains bound to its immutable template version after subsequent edits/publications.
5. Migration preserves existing project nodes, assignments, statuses, timestamps, and workbench records.
6. Specialist completion checks remain enforced when modules move to different node orders/keys; generic required fields are enforced.
7. API authorization, audit behavior, attachment ACL, and input validation are covered by tests.
8. Visual editor works with pointer and keyboard reordering, has usable empty/loading/error states, and shows a true detail preview.

## Out of scope

- Branching/parallel workflows, conditional transitions, per-field permissions, arbitrary custom components/code, and retroactively rebinding existing projects.
- Deleting or mutating remote Git branches.

# Project Level Field Implementation Plan

## Decision

Keep project priority as urgency and add a separate persisted project level for importance:

| Code | Label | Meaning |
| --- | --- | --- |
| 0 | 常规项目（C） | 日常改进或小型项目 |
| 1 | 重要项目（B） | 部门级重点，影响有限范围 |
| 2 | 关键项目（A） | 跨部门，影响业务线核心指标 |
| 3 | 战略项目（S） | 集团或公司最高层重点关注，直接影响战略 |

Existing projects migrate to `常规项目（C）`; new projects default to `常规项目（C）`. The field is editable in the kickoff project profile immediately after priority and is included in the project create/update/detail contract. Priority remains the urgency dimension; project level records importance and does not automatically replace approval.

## Tasks

- [x] Add backend enum, entity/DTO fields, service mapping, audit snapshot, schema migration, and tests.
- [x] Add frontend enum/type, project profile form binding, localized labels, and structure tests.
- [x] Update business/user documentation and run full verification.

# DSH Fork 维护与上游同步指南

> 适用对象：`xiebinJava/deepseek-harness`（fork），上游 `deepseek-ai/deepseek-harness`。
> 记录时间：2026-09-21。本文件是本地参考文档，未提交。

## 一、分支模型（2026-09-22 起，三层）

| 引用 | 角色 | 说明 |
| --- | --- | --- |
| `upstream` | 上游（只读） | `https://github.com/deepseek-ai/deepseek-harness.git`，push URL 已设为 `DISABLED` |
| `origin` | 你的 fork | 推 `master` / `release` / 功能分支；`refs/heads/*` 通配已恢复 |
| `master` | **纯上游镜像** | 只等于上游最新（当前 `ddefc45`），**不放任何自己的提交**；跟踪 `upstream/master` |
| `release` | **你的发版主线** | = `master`（上游最新）+ 你的全部改动（当前 `2cda31e2`）；跟踪 `origin/release` |
| 功能分支（如 `feat/xxx`） | 日常开发 | 从 `release` 切出，做完并回 `release` |
| `codex/pms-dsh-capability-protocol` | 历史分支 | 2026-09-21 首次同步时的集成分支，内容与 `release` 相同；可留作历史或删除 |
| `backup/pms-pre-sync` | 本地备份 | 2026-09-21 同步前的 `ccefd2a`，确认无问题后可删 |

三条线的职责一句话：**master 只跟上游；release 是你能用的完整版本；功能分支临时。**

原则：**改动尽量做成"加法"**（新增包/资源），上游文件只做最小接入；同步用 rebase，不用大分支长期漂移。

**上游只读（2026-09-21 决定）**：不向上游提交、不开 PR——上游是别人的项目。所有改动只留在自己的 fork；`upstream` 远端的 push URL 已设为 `DISABLED`，误推会直接失败。代价是侵入上游文件的那部分要按下方"冲突预算表"每次重贴，因此优先把改动做成加法、把侵入面压到最小。

## 二、常规同步

### 2.1 取上游最新到 master（镜像，安全无冲突）

```bash
cd /Users/fs/Desktop/Project/deepseek-harness
git fetch --no-tags upstream master
git branch -f master upstream/master   # 只允许对 master 这样做
git log --oneline -1 master            # 看一眼取到哪个版本
```

### 2.2 把上游新代码并进 release（你的发版主线）

```bash
git checkout release
git merge master                       # 等价于 merge upstream/master
# 有冲突：按下方"冲突预算表"处理；解完再次确认锁文件
NODE_EXTRA_CA_CERTS=/etc/ssl/cert.pem pnpm install --lockfile-only --no-frozen-lockfile \
  --registry https://registry.npmjs.org/
# 验证（本机可跑的部分）
./node_modules/.bin/vitest run packages/pms/dsh-pms packages/client/connection packages/preset/agent-presets
git push origin release
```

### 2.3 功能分支

```bash
git checkout -b feat/xxx release       # 从 release 切出
# …开发…
git checkout release && git merge feat/xxx   # 做完并回 release（或先推分支做评审）
```

- **⚠️ 只能对 `master` 用 `git branch -f`**（它是纯镜像）。对 `release` 或功能分支执行 `git branch -f release master` 会**直接丢掉你自己的代码**，永远不要这么做。
- 建议节奏：跟随上游 release tag（`release(dsh) …`），不必追每次 master 提交；同步后立刻验证再推 `release`。

## 二之一、备选：在独立工作分支上跟随上游（rebase 方式）

如果你不想直接在主线上解冲突，也可以保留一条独立工作分支，用 rebase 跟随上游，然后把结果并回 `master`。这条路径等价于 2026-09-21 那次操作：

```bash
cd /Users/fs/Desktop/Project/deepseek-harness
git config rerere.enabled true          # 只做一次：记住重复冲突的解法

# 1) 你的分支 rebase 到上游基线
git checkout codex/pms-dsh-capability-protocol
git branch -f backup/pms-pre-sync HEAD  # 每次同步前留一个回退点
git rebase upstream/master

# 2) 解决冲突（见下方"冲突预算表"），然后重新生成锁文件
NODE_EXTRA_CA_CERTS=/etc/ssl/cert.pem pnpm install --lockfile-only --no-frozen-lockfile \
  --registry https://registry.npmjs.org/
NODE_EXTRA_CA_CERTS=/etc/ssl/cert.pem pnpm install --frozen-lockfile --lockfile-only \
  --registry https://registry.npmjs.org/     # 必须通过，CI 用的是这个

# 3) 验证（本机可跑的部分）
./node_modules/.bin/vitest run packages/pms/dsh-pms packages/client/connection packages/preset/agent-presets
./node_modules/.bin/tsc -b packages/pms/dsh-pms/tsconfig.json

# 4) 推自己的分支（只动自己的分支，master 不碰）
git push --force-with-lease origin codex/pms-dsh-capability-protocol
```

不急着用上游新功能时，这一段可以一直不做——只要上游没动你的接入点，晚几周再 rebase 也不会更麻烦。

## 三、冲突预算表

「上游对应扩展点」= 下次上游再重构时，优先改用的机制；「本次验证」= 2026-09-21 同步时的证据。

### A. 纯新增（零冲突，优先级最高）

| 路径 | 说明 |
| --- | --- |
| `packages/pms/dsh-pms/**` | PMS 插件：契约注入、工具、live E2E 规格 |
| `packages/client/ui-pms-workspace/**` | PMS 工作区客户端（auth/context/refresh 桥） |
| `packages/preset/agent-presets/presets/pms-project-assistant/**` | PMS 项目助手 preset（数据文件） |
| `scripts/start-dsh-pms-sso.sh` | 本地启动脚本 |

### B. 最小接入（上游文件各改 1–3 行）

| 文件 | 改动 | 下次同步怎么做 | 本次验证 |
| --- | --- | --- | --- |
| `tsconfig.base.json` / `tsconfig.host.json` / `tsconfig.client.json` | 各 +1~2 行，指向 PMS 包 | 冲突时保留上游条目 + 追加 PMS 两条 | tsc 通过 |
| `packages/api/remotes/package.json` | +1 依赖 `@deepseek-ai/dsh-pms` | 保留上游新增依赖 + 我们的 | 已合并且无冲突 |
| `packages/api/remotes/src/client/index.ts` | +1 项 `pmsContextRemote` | 追加到上游数组末尾 | 已合并且无冲突 |
| `packages/bundle/web-app/cordis.patch.yml` / `package.json` | +4 / +1 行，挂 PMS bundle | 同上 | 未单独验证 |

### C. 中等侵入（每次同步都要复验）

| 文件 | 改动 | 下次同步怎么做 | 本次验证 |
| --- | --- | --- | --- |
| `packages/api/session-controller/src/agent.ts`、`commands.ts`、`types.ts` | +48/+28/+10：暴露并写入 `agentPreset`、`agentCompositionFingerprint` 投影 + preset 切换命令 | 上游仍保留 `projections` 机制，重新贴回即可 | 类型检查通过；client 规格本机跑不了（见环境问题） |
| `packages/api/session-controller/src/client/contract/snapshot.ts`、`client/sessions/session.ts` | +4/+12：快照暴露这两个字段 | 上游已删除 `queue` 字段，**不要再把它贴回来** | 同上 |
| `packages/client/connection/src/browser-auth.ts`、`src/index.ts` | +237/+13：DSH 浏览器 OIDC 登录 + 回调路由 | 回调路由现在**仅在 `BrowserAuth.oidcEnabled` 时注册**，保持这个条件即可不改上游测试 | 324 项测试通过（含上游 `node-half.host.spec.ts`） |
| `packages/client/connection/tests/browser-auth.host.spec.ts`、README（中英） | 我们新增的用例与文档 | 冲突时优先保留上游正文 + 追加我们的用例 | 通过 |
| `apps/web/tests/expected/agent-preset-*/**.md`、`apps/web/tests/agent-preset-selection.e2e.ts`、`apps/cli/tests/web-agent-presets.e2e.ts` | 快照/期望文件 | **不要手合并**：优先取上游，再按需 `DSH_SNAPSHOT=refresh` 重录 | 未验证（需完整依赖） |

### D. 大侵入 / 待决策

| 文件 | 改动 | 决策建议 |
| --- | --- | --- |
| `packages/preset/agent-presets/src/*`（authoring.ts +345、index.ts +165、types.ts +112…）及测试 | preset 编写/编辑器功能（约 700 行） | 不提上游（决定见上）。三条可选路线：① 保留并每次按本表重贴（`rerere` 已开启，重复冲突会自动套用旧解法）；② 只保留实际用到的子功能，把侵入压到最小；③ 直接砍掉该功能。建议先观察一两个同步周期，再决定是否走 ② |
| `packages/client/ui-agent-preset/**` | 原改动：把 Agent 选择器放进输入框 + 不可用态提示 | 本次已**整体回退到上游**（上游重写了同一块组件）。若要保留该 UX，请基于上游新的 seat/slot API 作为独立小提交重做 |
| `packages/client/ui-conversation/src/client/skeleton/ConversationContent.tsx`（+4） | 小改动 | 复查后决定是否仍需要 |

## 四、环境问题清单（与代码无关，但会卡住 CI/本地验证）

| 问题 | 现象 | 处理建议 |
| --- | --- | --- |
| 磁盘空间 | 460 GB 中仅剩 ~2.4 GB，安装时报 `ERR_PNPM_ENOSPC` | `pnpm store prune`、Docker/Colima 清理、`~/Library/Caches` |
| npm tarball 慢 | `registry.npmjs.org` 实测 ~5 KB/s，`@openai/codex`、`@anthropic-ai/claude-agent-sdk` 下不完 | 配代理或换可用的镜像源；`--lockfile-only` 不受影响 |
| pre-push `typecheck` 钩子 | 依赖自检会尝试联网安装，超时失败 | 依赖装好后重跑 `pnpm run typecheck`；必要时 `LEFTHOOK_EXCLUDE=typecheck` 临时跳过 |
| `third-party notices` 钩子 | 缺 `@anthropic-ai/claude-agent-sdk-darwin-arm64`，生成器读不到包 | 完整安装后重跑 `node_modules/.bin/tsx scripts/gen-third-party-notices.ts` 并确认 `THIRD_PARTY_NOTICES.md` 无变化 |
| `session-controller`/`ui-agent-preset` client 规格 | 报 `cannot resolve plugin package @deepseek-ai/dsh-plugin-manager`（workspace 链接残缺） | 需要一次完整 `pnpm install` |

## 五、2026-09-21 同步记录

- 上游基线：`ddefc45fbc`（原基线 `0d1f50007f`，落后 18,052 个提交）。
- 冲突：10 个文件、19 个冲突块；处理方式见上表（`ui-agent-preset` 整体取上游，`session-controller` 保留我们的字段并删除上游已移除的 `queue`，两个 `package.json`/`tsconfig` 合并，锁文件重新生成）。
- 推送：`git push --force-with-lease` 成功，远端分支 `ccefd2a180 → 2cda31e258`；`master` 未改动（仍等于上游 `ddefc45`）。
- 验证：`vitest run packages/pms/dsh-pms packages/client/connection packages/preset/agent-presets` → **369 passed / 7 skipped**；`tsc -b packages/pms/dsh-pms` 与 `packages/client/connection` 通过；client 测试运行时依赖残缺的部分未能运行。

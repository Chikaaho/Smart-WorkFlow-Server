# Smart-WorkFlow（后端）

> **嵌入 AI Agent 的企业级低代码 OA 平台后端**。
> Smart-WorkFlow 采用模块化单体（Modular Monolith）架构：动态表单引擎、BPM 流程自动化、通知、存储、定时任务、IoT 与 AI Agent 能力分层承载。
> 本仓库是 Smart-WorkFlow 三仓之一；配套前端 [Smart-WorkFlow-Web](../Smart-WorkFlow-Web/) 与规划知识库 [Smart-WorkFlow-Knowledge](../)。

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 / 运行时 | Java 21（LTS） |
| 框架 | Spring Boot 3.4.4 |
| 持久层 | MyBatis-Plus |
| 流程引擎 | Flowable 7.1.0 |
| 数据库 | PostgreSQL（生产 / local）、H2（开发内存，SQL 正确性代理） |
| 数据库迁移 | Flyway（PostgreSQL + H2 双方言） |
| 认证鉴权 | JWT + Spring Security |
| API 文档 | Springdoc OpenAPI |
| 缓存 | Redis（可选） |
| 存储 | 策略模式（Local / MinIO / COS / Qiniu） |
| 对象解析 / 知识库 | Tika / PDFBox / Jsoup |
| 向量检索 | pgvector（RAG 骨架） |

> **不再支持 MySQL**。开发环境默认使用 H2 内存作为 SQL 正确性代理；`local` profile 使用本地 PostgreSQL。

---

## 四层模块架构

```
Smart-WorkFlow/
├── pom.xml                    # 根聚合 POM
├── README.md
│
├── sw-dependencies/           # ═══ BOM 版本管理 ═══
│   └── pom.xml                # 统一管控第三方依赖版本
│
├── sw-framework/              # ═══ 内核层 ═══
│   ├── sw-common/             # 公共基础设施（基类/错误码/分页/多租户）
│   └── sw-security/           # 认证鉴权（JWT + Spring Security）
│
├── sw-basic/                  # ═══ 基础能力层 ═══
│   ├── sw-basic-storage/      # 文件存储（Local/MinIO/COS/Qiniu）
│   ├── sw-basic-notify/       # 通知（站内信/模板/批量发送）
│   ├── sw-basic-job/          # 定时任务（Quartz）
│   ├── sw-basic-iot/          # IoT 设备接入（腾讯云接入最小闭环）
│   ├── sw-basic-knowledge/    # 知识库与 RAG（骨架）
│   └── sw-basic-agent/        # AI Agent（会话/消息/工具调用/图编排）
│
├── sw-biz/                    # ═══ 业务层 ═══
│   ├── sw-biz-system/  (-api/-biz)  # 身份/组织/RBAC/字典
│   ├── sw-biz-form/    (-api/-biz)  # 低代码表单引擎（动态宽表）
│   ├── sw-biz-notify/  (-api/-biz)  # 通知业务
│   ├── sw-bpm/                        # 流程引擎
│   │   ├── sw-bpm-api/                # 契约 + DTO + SPI
│   │   ├── sw-bpm-engine/             # 防腐层（运行期/外部数据源）
│   │   └── sw-bpm-process/            # 流程业务（待办/审批/部署）
│   └── sw-biz-openapi/  (-api/-biz)   # 开放接口层
│
└── sw-bootstrap/              # ═══ 唯一启动入口 ═══
    ├── src/main/java/com/sw/ck/bootstrap/StarterApplication.java
    └── src/main/resources/db/migration/{h2,postgresql}/   # Flyway 双方言迁移
```

### 依赖方向（自顶向下，不可反向）

```
sw-dependencies
  └─ sw-framework : sw-common, sw-security
       └─ sw-basic : storage, notify, job, iot, knowledge, agent
            └─ sw-biz : system, form, notify, bpm (api/engine/process), openapi
                 └─ sw-bootstrap
```

业务模块拆 `-api`（契约/DTO/SPI）+ `-biz`（实现）；跨模块通信：无返回值 → Spring 事件，有返回值 → Facade 接口。**依赖方向自上而下不可反向**，跨模块不横向 import。

---

## 模块边界（当前完成度）

| 模块 | 状态 | 说明 |
|------|------|------|
| sw-common / sw-security | ✅ 完整 | 公共基础设施、JWT 鉴权 |
| sw-biz-system | ✅ 核心 | 用户/角色/部门/菜单/字典 CRUD + 数据权限 |
| sw-biz-form | ✅ 完整 | 动态宽表表单引擎（设计器 + 渲染 + 导入导出） |
| sw-bpm | ✅ 核心闭环 | 流程定义、发起/待办/审批/实例监控、BPMN 翻译、指定审批人 |
| sw-basic-notify | ✅ 完整 | 通知 CRUD、模板、批量发送 |
| sw-basic-storage | ✅ 完整 | 策略模式多存储提供商 |
| sw-basic-job | ✅ 完整 | Quartz BEAN+FLOW 双类型 |
| sw-basic-iot | 🟦 最小落地 | 腾讯云 IoT 最小接入闭环（Provider/命令/上线补发/回调） |
| sw-basic-agent / sw-basic-knowledge | 🟦 图编排就绪 | 图执行/调试/Prompt/Token 统计已落地；知识库 RAG 待扩展 |
| sw-biz-openapi | ⬜ 骨架 | 开放接口预留 |

> 完成度与测试基线是当前事实的**精简入口**，权威逐模块状态见 `Smart-WorkFlow/功能清单.md` 与规划知识库；本 README 不维护逐功能变更流水账。

---

## 环境要求

- JDK 21+
- Maven 3.8+
- PostgreSQL 15+（`local` / 生产）
- Redis（如需缓存相关能力，`dev` 默认 H2 不强制）
- MinIO / 云 COS / 七牛（如需对象存储，提供方连接凭据）

### 数据库模式

- 开发 `dev`：H2 内存（`application-dev.yml`）
- 本地 `local`：PostgreSQL（`application-local.yml`）
- 生产：`application.yml` + 环境变量注入敏感值

---

## 本地启动

```bash
# 方式一：开发模式（H2 内存，快速跑通）
cd sw-bootstrap
MAVEN_OPTS="-Xmx2g" mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 方式二：构建后运行 Jar（生产对应 local/pg 配置）
cd sw-bootstrap
MAVEN_OPTS="-Xmx2g" mvn clean package -DskipTests
java -jar target/sw-bootstrap.jar --spring.profiles.active=local
```

启动后端点：`http://localhost:8080/api`（context-path `/api`）。

---

## 构建 / 测试 / 迁移校验命令

> ⚠️ 所有 `mvn` 命令必须带 `MAVEN_OPTS="-Xmx2g"`（硬约束，最大内存 2G）；**前后端编译互斥**——执行编译/测试前需检测前端是否在编译，见规划知识库 `knowledge/shared-constraints.md` §9。

```bash
# 构建全部模块（跳过测试）
MAVEN_OPTS="-Xmx2g" mvn clean install -DskipTests

# 增量编译验证
MAVEN_OPTS="-Xmx2g" mvn -q compile

# 项目级全量测试（当前正式基线 955 项）
MAVEN_OPTS="-Xmx2g" mvn -q test
```

### Flyway 双方言迁移

- 迁移脚本目录：`sw-bootstrap/src/main/resources/db/migration/h2/`（H2）与 `.../postgresql/`（PostgreSQL）。
- 每个迁移脚本必须同时维护 H2 与 PG 双方言，版本号一致。
- **当前迁移终点：H2 `V44`（44 migrations）/ PostgreSQL `V44`（43 migrations）**（V41 为 H2 专用）。
- 永久迁移链测试：`FlywayFullChainH2Test` / `FlywayFullChainPostgresTest`（sw-bootstrap 测试）。

---

## 核心设计要点

### 动态宽表存储

表单提交数据不存 JSON 列，每个表单一张物理表（`sw_form_{nanoId}`），一行等于一次提交。

- 主表单：`sw_form_{nanoId}`；表格子表：`sw_form_table_{nanoId}`
- 配置表：`sw_form_config`（JSONB）；快照表：`sw_form_snapshot`（JSONB）
- 动态宽表裸 SQL 红线：每条裸 SQL 必须手写 `deleted` + `tenant_id`，列名过白名单，值用参数化绑定

### 关系原语：两档行为

| 档 | 语义 | 删除 |
|----|------|------|
| `TABLE` | 主表单内嵌子表（明细行） | **CASCADE** — 删主即连带软删子 |
| `REFERENCE` | 独立表单间引用 | **RESTRICT** — 有子引用则禁删父 |

### 开放核心 BPM

- `sw-bpm-engine` — **闭源防腐层**，承载引擎运行期与外部数据源执行
- `sw-bpm-api` 与 `sw-bpm-process` — **开源**，承载流程业务与契约

### 开发哲学

- **Walking Skeleton**：先打通一条端到端的薄切片（登录 → 简单表单 → 单节点审批 → 通知），再横向扩展任一模块。
- **契约先行 + 前后端并行**：前端拿契约与 mock 推页面，后端 seam 点亮后零改动接真数据。

---

## 权威文档导航

| 需求 | 入口 |
|------|------|
| 后端工程宪法（硬约束） | [`docs/governance/engineering-constitution.md`](docs/governance/engineering-constitution.md) |
| 工作区治理（角色/权限/流程） | 上级目录 [`system.md`](../system.md) |
| 功能清单（全平台 M01-M10） | [`功能清单.md`](功能清单.md) |
| 当前项目状态（唯一权威） | 上级目录 `knowledge/current-status.md` |
| 架构 / 已知问题 / 决策 | 上级目录 `knowledge/`（architecture / known-issues / decisions） |
| 共享约束（内存 / 互斥 / token） | 上级目录 `knowledge/shared-constraints.md` |

> 本 README 只作后端开发入口与导航，不维护逐功能变更日志；历史变化见 Git 历史与规划知识库。
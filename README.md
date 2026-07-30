# Smart-WorkFlow

**嵌入 AI Agent 的企业级低代码 OA 系统后端**

Smart-WorkFlow 是一个面向未来的企业级低代码 OA 平台后端，采用**模块化单体（Modular Monolith）** 架构设计。深度集成 AI Agent 能力，支持动态表单引擎、流程自动化、IoT 设备接入、知识库与 RAG 检索，致力于打造智能化的企业办公协同平台。

> 本仓库是 Smart-WorkFlow 三件套之一。配套前端 [Smart-WorkFlow-Web](https://github.com/Chikaaho/Smart-WorkFlow-Web)、规划知识库 [Smart-WorkFlow-Knowledge](https://github.com/Chikaaho/Smart-WorkFlow-Knowledge)。

## 技术栈

| 类别 | 技术 | 说明 |
|------|------|------|
| 语言 | Java 21 | LTS 版本，支持虚拟线程 |
| 框架 | Spring Boot 3.4.4 | |
| 持久层 | MyBatis-Plus 3.5.9 | 增强型 MyBatis |
| 流程引擎 | Flowable 7.1.0 | 兼容 Spring Boot 3 |
| 数据库迁移 | Flyway | PostgreSQL + H2 双方言 |
| 数据库 | PostgreSQL（主）+ H2（开发） | 动态宽表 + 固定元数据表 |
| 认证鉴权 | JWT + Spring Security | |
| AI Agent | Spring AI + LangGraph4j | Agent 编排 |
| 缓存 | Redis | |
| IoT | Spring Integration MQTT + Paho v5 | 硬件设备接入 |
| 存储 | MinIO | 对象存储 |
| 知识库 | Apache Tika + PDFBox + Jsoup | 文档解析 |
| 向量检索 | pgvector | RAG 检索 |
| 工具 | Hutool · MapStruct · Lombok · Hutool · Hutool | 生产力工具 |
| JSON | Jackson + fastjson2 | 出入参 Jackson，业务内按需选 |
| HTTP（业务侧） | hutool `HttpRequest` | Webhook / 第三方集成 |
| API 文档 | Springdoc OpenAPI | |
| 监控 | Micrometer Tracing + OpenTelemetry | 可观测性 |

> 注意：生产数据库为 PostgreSQL，开发环境使用 H2 作为 SQL 正确性代理。**不再支持 MySQL。**

## 四层模块架构

```
Smart-WorkFlow/
├── pom.xml                                   # 根父工程，聚合所有模块
├── README.md
│
├── sw-dependencies/                          # ══════ BOM 版本管理 ══════
│   └── pom.xml                               # 统一管控所有第三方依赖版本
│
├── sw-framework/                             # ══════ 内核层 ══════
│   ├── sw-common/                            # 公共基础设施（基类/错误码/分页/多租户）
│   └── sw-security/                          # 认证鉴权（JWT + Spring Security）
│
├── sw-basic/                                 # ══════ 基础能力层 ══════
│   ├── sw-basic-storage/                     # 文件存储
│   ├── sw-basic-notify/                      # ✅ 通知（站内信/模板/发送记录）
│   ├── sw-basic-job/                         # 定时任务（Quartz 单节点）
│   ├── sw-basic-iot/                         # IoT 设备接入
│   ├── sw-basic-knowledge/                   # 知识库与 RAG
│   └── sw-basic-agent/                       # AI Agent（会话/消息/工具调用）
│
├── sw-biz/                                   # ══════ 业务层 ══════
│   ├── sw-biz-system/          (-api/-biz)   # 身份/组织/RBAC/字典/参数
│   ├── sw-biz-form/            (-api/-biz)   # ✅ 低代码表单引擎（动态宽表）
│   ├── sw-bpm/                              # 流程引擎
│   │   ├── sw-bpm-api/                      # 契约 + DTO + SPI
│   │   ├── sw-bpm-engine/                   # 闭源防腐层（外部数据源执行）
│   │   └── sw-bpm-process/                  # 流程业务（待办/审批/部署）
│   └── sw-biz-openapi/        (-api/-biz)   # 开放接口层
│
└── sw-bootstrap/                             # ══════ 唯一启动入口 ══════
    └── src/main/java/com/sw/ck/bootstrap/
        └── StarterApplication.java           # 启动类 + Flyway + 启动自检
```

### 依赖方向（自顶向下，不可反向）

```
sw-dependencies
  └─ sw-framework : sw-common, sw-security
       └─ sw-basic : storage, notify, job, iot, knowledge, agent
            └─ sw-biz : system, form, bpm (api/engine/process), openapi
                 └─ sw-bootstrap
```

## 当前完成度

| 模块 | 状态 | 说明 |
|------|------|------|
| sw-common | ✅ 完整 | 31 个 Java 文件 — 公共基础设施 |
| sw-security | ✅ 完整 | 23 个 Java 文件 — JWT + refresh token 双认证全链路 |
| sw-biz-system | ✅ 核心就位 | 49 个 Java 文件 — 用户/角色/菜单/部门/字典 CRUD |
| sw-biz-form | ✅ 已封版 | 47 个 Java 文件 + 7 个测试类 — 动态宽表引擎完整实现 |
| sw-bpm | 🟦 开发中 | 78 个 Java 文件 — 待办/审批/流程定义/实例监控/流程监控端点 |
| sw-biz-openapi | ⬜ 骨架 | 仅 package-info |
| sw-basic-notify | ✅ 就位 | Facade + Controller + Mapper + 测试 + Flyway |
| sw-basic-storage | ✅ 完整 | 策略模式 + 4 存储提供商（Local/MinIO/COS/Qiniu） |
| sw-basic-job | ✅ 完整 | Quartz BEAN+FLOW 双类型 + Controller + Facade + 测试 |
| sw-basic-iot | ⬜ 骨架 | AutoConfiguration 占位 |
| sw-basic-knowledge | ⬜ 骨架 | AutoConfiguration 占位 |
| sw-basic-agent | ⬜ 骨架 | AutoConfiguration 占位 |
| Flyway 迁移 | ✅ 17 个版本 | V1–V17，PG + H2 双方言 |

**测试基线：465 tests / 0 failures / 0 errors**（CONFIRMED 2026-07-28）

## 设计要点

### 动态宽表存储

表单提交数据不存 JSON 列，每个表单一张物理表（`sw_form_{nanoId}`），一行等于一次提交。支持原生 SQL 查询与索引，不采用 EAV 或单 JSON 列方案。

- 主表单：`sw_form_{nanoId}`
- 表格子表：`sw_form_table_{nanoId}`
- 配置表：`sw_form_config`（JSONB）
- 快照表：`sw_form_snapshot`（JSONB）

### 关系原语：两档行为

| 档 | 语义 | 删除 |
|----|------|------|
| `TABLE` | 主表单内嵌子表（明细行） | **CASCADE** — 删主即连带软删子 |
| `REFERENCE` | 独立表单间引用 | **RESTRICT** — 有子引用则禁删父 |

### Walking Skeleton 开发哲学

先打通一条端到端的薄切片，而非任一模块的横向铺满。里程碑：**登录 → 简单表单 → 单节点审批 → 通知** 端到端跑通，之后才横向扩展任一模块。

### open-core BPM

- `sw-bpm-engine` — **闭源防腐层**，承载引擎运行期与外部数据源执行
- `sw-bpm-api` 和 `sw-bpm-process` — **开源**，承载流程业务与契约

### 代码规范强约束

详细的模块依赖铁律、表命名规则（`sys_` / `sw_form_` / `sw_bpm_` 等）、动态宽表裸 SQL 安全红线（手写 `deleted` + `tenant_id`）、错误码区间等，参见 [system.md](.claude/system.md)。

## 构建方式

```bash
# 构建全部模块（跳过测试）
mvn clean install -DskipTests

# 全量编译（增量验证用）
mvn -q compile

# 全量测试
mvn -q test
```

## 启动方式

```bash
# 方式一：使用 Spring Boot Maven Plugin
cd sw-bootstrap
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 方式二：编译后直接运行 Jar
cd sw-bootstrap
mvn clean package -DskipTests
java -jar target/sw-bootstrap.jar --spring.profiles.active=dev
```

### 启动要求

- JDK 21+
- Maven 3.8+
- PostgreSQL 15+（如需启动数据库相关功能）
- Redis 7.0+（如需启动缓存相关功能）
- MinIO（如需启动文件存储相关功能）

## 配置说明

### 配置文件结构

| 文件 | 用途 | 说明 |
|------|------|------|
| `application.yml` | 主配置 | 包含全部配置项骨架和占位符 |
| `application-dev.yml` | 开发环境 | 覆盖开发环境特定配置 |

### 关键配置项

所有敏感信息（密码、Token、API Key）均使用占位符，通过环境变量或启动参数注入：

```bash
# 通过环境变量注入
export DATASOURCE_USERNAME=postgres
export DATASOURCE_PASSWORD=your_password
export OPENAI_API_KEY=sk-your-key

# 或通过启动参数传入
java -jar sw-bootstrap.jar \
  --DATASOURCE_USERNAME=postgres \
  --DATASOURCE_PASSWORD=your_password
```

### 开发约定

| 模块 | artifactId | 包路径 |
|------|-----------|--------|
| 依赖管理 | `sw-dependencies` | 无 Java 代码 |
| 启动入口 | `sw-bootstrap` | `com.sw.ck.bootstrap` |
| 公共 | `sw-common` | `com.sw.ck.common` |
| 安全 | `sw-security` | `com.sw.ck.security` |
| 系统管理 | `sw-biz-system` | `com.sw.ck.system` |
| 表单引擎 | `sw-biz-form` | `com.sw.ck.form` |
| 流程引擎 | `sw-bpm-*` | `com.sw.ck.bpm.*` |
| 通知 | `sw-basic-notify` | `com.sw.ck.notify` |
| Agent | `sw-basic-agent` | `com.sw.ck.agent` |
| 知识库 | `sw-basic-knowledge` | `com.sw.ck.knowledge` |
| IoT | `sw-basic-iot` | `com.sw.ck.iot` |
| 存储 | `sw-basic-storage` | `com.sw.ck.storage` |
| 定时任务 | `sw-basic-job` | `com.sw.ck.job` |
| 开放接口 | `sw-biz-openapi` | `com.sw.ck.openapi` |

---

**Smart-WorkFlow** — 始于智能，不止于流程。

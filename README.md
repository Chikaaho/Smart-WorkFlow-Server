# Smart-WorkFlow-aPaaS-server

Smart-WorkFlow-aPaaS-server 是 CH-aPaaS 的后端 API 服务，基于 Java 21 与 Spring Boot 构建。它以模块化单体承载低代码表单、流程自动化、组织权限、通知、存储、任务、IoT、知识库和 AI Agent 等业务域。

配套入口：[Smart-WorkFlow-aPaaS-Web](../Smart-WorkFlow-Web/README.md) · [项目知识中心](../README.md)

## 核心能力

- 身份、组织、角色、菜单、字典与数据权限。
- 动态表单定义、发布、数据提交、子表与引用关系。
- BPMN 流程定义、发起、待办、审批和实例监控。
- 通知、文件存储与定时任务等通用服务。
- AI Agent 会话、工具调用、图编排与知识能力。
- IoT 设备接入与业务流程联动。
- OpenAPI 文档、数据库迁移与多环境配置。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 语言与框架 | Java 21、Spring Boot 3.4 |
| 数据访问 | MyBatis-Plus、JdbcTemplate |
| 流程引擎 | Flowable |
| 数据库 | PostgreSQL、H2、Flyway |
| 认证与缓存 | Spring Security、JWT、Redis |
| API 文档 | Springdoc OpenAPI |
| 调度与存储 | Quartz、Local / MinIO / COS / Qiniu |

## 模块结构

```text
Smart-WorkFlow-Server/
├── sw-dependencies/       依赖版本管理
├── sw-framework/          公共基础设施与安全
├── sw-basic/              存储、通知、任务、IoT、知识库与 Agent
├── sw-biz/                系统、表单、流程与开放接口
└── sw-bootstrap/          应用启动、配置与数据库迁移
```

模块依赖由基础层流向业务层，`sw-bootstrap` 是应用启动入口。完整边界见[后端工程宪法](docs/governance/engineering-constitution.md)。

## 环境要求

- JDK 21
- Maven 3.8 或更高版本
- Redis 7 或兼容版本
- PostgreSQL 15 或更高版本（本地 PostgreSQL 与生产环境）
- `SW_CIPHER_KEY`：Base64 编码的 32 字节密钥，用于加密敏感配置

数据库模式与配置入口：

- `dev`：H2 内存数据库，配置见 [`application-dev.yml`](sw-bootstrap/src/main/resources/application-dev.yml)。
- `local`：本地 PostgreSQL，配置见 [`application-local.yml`](sw-bootstrap/src/main/resources/application-local.yml)。
- 通用配置：[`application.yml`](sw-bootstrap/src/main/resources/application.yml)。

Redis 承载认证登录态。对象存储服务只在选择对应存储提供方时需要准备。

## 快速开始

在仓库根目录生成并注入开发密钥：

```bash
export SW_CIPHER_KEY=$(openssl rand -base64 32)
```

确认 Redis 可用：

```bash
redis-cli ping
```

进入启动模块：

```bash
cd sw-bootstrap
```

使用 H2 启动开发环境：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

使用 PostgreSQL 启动本地环境：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

服务默认地址为 `http://localhost:8080/api`。应用启动后可通过 `http://localhost:8080/api/swagger-ui/index.html` 浏览 API 文档，通过 `http://localhost:8080/api/v3/api-docs` 获取 OpenAPI 描述。

## 常用开发命令

以下命令在后端仓库根目录执行。

编译全部模块：

```bash
mvn compile
```

运行测试：

```bash
mvn test
```

构建可运行产物：

```bash
mvn clean package
```

跳过测试构建：

```bash
mvn clean package -DskipTests
```

## 进一步阅读

| 主题 | 入口 |
| --- | --- |
| 后端工程边界与开发规范 | [`docs/governance/engineering-constitution.md`](docs/governance/engineering-constitution.md) |
| 平台整体架构 | [`../knowledge/architecture.md`](../knowledge/architecture.md) |
| 正式功能清单 | [`功能清单.md`](功能清单.md) |
| 工作区治理入口 | [`../system.md`](../system.md) |
| 前端开发入口 | [`../Smart-WorkFlow-Web/README.md`](../Smart-WorkFlow-Web/README.md) |

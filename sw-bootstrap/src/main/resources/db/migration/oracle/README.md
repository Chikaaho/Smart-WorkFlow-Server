# Oracle Migration Scripts

部署 Oracle 前请补齐本目录迁移脚本，参考 `postgresql/` 目录。

类型对照：
- deleted: NUMBER(3)
- 长文本: CLOB
- 其他列类型适配 (NUMBER(19) for BIGINT, VARCHAR2(n), TIMESTAMP)

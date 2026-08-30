-- ===================================================================
-- Smart-WorkFlow :: V17: 初始化定时任务调度表 (PostgreSQL)
-- ===================================================================
CREATE TABLE sw_job_info (
    id              BIGINT      NOT NULL,
    job_name        VARCHAR(128) NOT NULL,
    job_group       VARCHAR(128) NOT NULL DEFAULT 'DEFAULT',
    job_type        VARCHAR(16)  NOT NULL DEFAULT 'BEAN',
    cron_expression VARCHAR(128) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    concurrent      SMALLINT     NOT NULL DEFAULT 0,
    misfire_policy  SMALLINT     NOT NULL DEFAULT 0,
    description     VARCHAR(512) DEFAULT NULL,
    bean_name       VARCHAR(256) DEFAULT NULL,
    bean_params     TEXT         DEFAULT NULL,
    flow_def_key    VARCHAR(128) DEFAULT NULL,
    form_data       TEXT         DEFAULT NULL,
    last_fire_time  TIMESTAMP    DEFAULT NULL,
    next_fire_time  TIMESTAMP    DEFAULT NULL,
    create_time     TIMESTAMP    DEFAULT NULL,
    create_by       BIGINT       DEFAULT NULL,
    update_time     TIMESTAMP    DEFAULT NULL,
    update_by       BIGINT       DEFAULT NULL,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    tenant_id       BIGINT       NOT NULL DEFAULT 0,
    version         BIGINT       DEFAULT NULL,
    PRIMARY KEY (id)
);

COMMENT ON TABLE  sw_job_info               IS '定时任务定义';
COMMENT ON COLUMN sw_job_info.id            IS '主键';
COMMENT ON COLUMN sw_job_info.job_name      IS '任务名称';
COMMENT ON COLUMN sw_job_info.job_group     IS '任务组（Quartz JobKey 分组）';
COMMENT ON COLUMN sw_job_info.job_type      IS '任务类型（BEAN=处理器 / FLOW=发起流程）';
COMMENT ON COLUMN sw_job_info.cron_expression IS 'Cron 表达式';
COMMENT ON COLUMN sw_job_info.status        IS '任务状态（NORMAL=启用 / PAUSED=停用）';
COMMENT ON COLUMN sw_job_info.concurrent    IS '是否允许并发（0=否 / 1=是）';
COMMENT ON COLUMN sw_job_info.misfire_policy IS 'Misfire 策略（0=忽略 / 1=立即触发 / 2=放弃）';
COMMENT ON COLUMN sw_job_info.description   IS '任务描述';
COMMENT ON COLUMN sw_job_info.bean_name     IS 'Spring Bean 名称（BEAN 类型必填）';
COMMENT ON COLUMN sw_job_info.bean_params   IS 'Bean 方法参数（JSON）';
COMMENT ON COLUMN sw_job_info.flow_def_key  IS '流程定义 Key（FLOW 类型必填）';
COMMENT ON COLUMN sw_job_info.form_data     IS '表单数据（JSON）';
COMMENT ON COLUMN sw_job_info.last_fire_time IS '上次执行时间';
COMMENT ON COLUMN sw_job_info.next_fire_time IS '下次计划执行时间';
COMMENT ON COLUMN sw_job_info.create_time   IS '创建时间';
COMMENT ON COLUMN sw_job_info.create_by     IS '创建人';
COMMENT ON COLUMN sw_job_info.update_time   IS '更新时间';
COMMENT ON COLUMN sw_job_info.update_by     IS '更新人';
COMMENT ON COLUMN sw_job_info.deleted       IS '逻辑删除标记（0=未删, 1=已删）';
COMMENT ON COLUMN sw_job_info.tenant_id     IS '租户 ID';
COMMENT ON COLUMN sw_job_info.version       IS '乐观锁版本号';

CREATE INDEX idx_sw_job_info_tenant_deleted ON sw_job_info (tenant_id, deleted);

CREATE TABLE sw_job_log (
    id              BIGINT      NOT NULL,
    job_id          BIGINT      NOT NULL,
    job_name        VARCHAR(128) NOT NULL,
    job_group       VARCHAR(128) NOT NULL DEFAULT 'DEFAULT',
    trigger_type    VARCHAR(16)  NOT NULL DEFAULT 'AUTO',
    job_params      TEXT         DEFAULT NULL,
    exec_status     VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    start_time      TIMESTAMP    DEFAULT NULL,
    end_time        TIMESTAMP    DEFAULT NULL,
    duration        BIGINT       DEFAULT NULL,
    result_msg      TEXT         DEFAULT NULL,
    exception_stack TEXT         DEFAULT NULL,
    create_time     TIMESTAMP    DEFAULT NULL,
    create_by       BIGINT       DEFAULT NULL,
    update_time     TIMESTAMP    DEFAULT NULL,
    update_by       BIGINT       DEFAULT NULL,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    tenant_id       BIGINT       NOT NULL DEFAULT 0,
    version         BIGINT       DEFAULT NULL,
    PRIMARY KEY (id)
);

COMMENT ON TABLE  sw_job_log                IS '定时任务执行日志';
COMMENT ON COLUMN sw_job_log.id             IS '主键';
COMMENT ON COLUMN sw_job_log.job_id         IS '关联任务 ID（sw_job_info.id）';
COMMENT ON COLUMN sw_job_log.job_name       IS '任务名称（冗余）';
COMMENT ON COLUMN sw_job_log.job_group      IS '任务组（冗余）';
COMMENT ON COLUMN sw_job_log.trigger_type   IS '触发方式（AUTO=定时 / MANUAL=手动）';
COMMENT ON COLUMN sw_job_log.job_params     IS '任务参数快照';
COMMENT ON COLUMN sw_job_log.exec_status    IS '执行状态（RUNNING / SUCCESS / FAILED）';
COMMENT ON COLUMN sw_job_log.start_time     IS '执行开始时间';
COMMENT ON COLUMN sw_job_log.end_time       IS '执行结束时间';
COMMENT ON COLUMN sw_job_log.duration       IS '执行耗时（毫秒）';
COMMENT ON COLUMN sw_job_log.result_msg     IS '执行结果/异常信息';
COMMENT ON COLUMN sw_job_log.exception_stack IS '异常堆栈（仅失败时记录）';
COMMENT ON COLUMN sw_job_log.create_time    IS '创建时间';
COMMENT ON COLUMN sw_job_log.create_by      IS '创建人';
COMMENT ON COLUMN sw_job_log.update_time    IS '更新时间';
COMMENT ON COLUMN sw_job_log.update_by      IS '更新人';
COMMENT ON COLUMN sw_job_log.deleted        IS '逻辑删除标记（0=未删, 1=已删）';
COMMENT ON COLUMN sw_job_log.tenant_id      IS '租户 ID';
COMMENT ON COLUMN sw_job_log.version        IS '乐观锁版本号';

CREATE INDEX idx_sw_job_log_job_id ON sw_job_log (job_id);
CREATE INDEX idx_sw_job_log_tenant_deleted ON sw_job_log (tenant_id, deleted);

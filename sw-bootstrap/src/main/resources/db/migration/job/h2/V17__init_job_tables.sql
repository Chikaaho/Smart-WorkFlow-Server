-- ===================================================================
-- Smart-WorkFlow :: V17: 初始化定时任务调度表 (H2)
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

CREATE INDEX idx_sw_job_log_job_id ON sw_job_log (job_id);
CREATE INDEX idx_sw_job_log_tenant_deleted ON sw_job_log (tenant_id, deleted);

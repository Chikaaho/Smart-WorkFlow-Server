package com.sw.ck.bpm.engine.service;

import com.sw.ck.bpm.engine.entity.ExternalDatasource;
import com.sw.ck.common.service.BaseService;

/**
 * 外部数据源 Service。
 */
public interface ExternalDatasourceService extends BaseService<ExternalDatasource> {

    /**
     * 保存外部数据源（plaintextPassword 加密后存入 password_cipher）。
     *
     * @param entity     实体（passwordCipher 字段携带明文密码）
     * @param plaintextPassword 明文密码（会被加密后写入 entity.passwordCipher，不落盘）
     */
    void saveWithEncryption(ExternalDatasource entity, String plaintextPassword);

    /**
     * 更新外部数据源（若提供新明文密码则加密后更新，否则保留旧密文）。
     *
     * @param entity     实体
     * @param plaintextPassword 新明文密码（null 表示不修改密码）
     */
    void updateWithEncryption(ExternalDatasource entity, String plaintextPassword);

    /**
     * 获取解密后的明文密码（仅用于内部执行 SQL，绝不对外暴露）。
     *
     * @param entity 实体（须已携带 passwordCipher）
     * @return 明文密码
     */
    String decryptPassword(ExternalDatasource entity);
}

package com.sw.ck.notify.controller;

import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.response.R;
import com.sw.ck.notify.entity.NotifyMessage;
import com.sw.ck.notify.service.NotifyMessageService;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知消息接收侧控制器（M05 Step 3 — 第四环闭合）。
 * <p>
 * 提供两个接口：
 * <ul>
 *   <li>{@code GET /notify/messages} — 当前用户的未读/已读通知列表</li>
 *   <li>{@code POST /notify/messages/{id}/read} — 标记已读（带越权校验）</li>
 * </ul>
 * </p>
 *
 * <h3>鉴权</h3>
 * 两接口均需登录（默认走过滤器链，无需加 permit 白名单）。
 *
 * <h3>越权</h3>
 * {@code read} 前置校验消息 {@code recipient_id} 必须等于当前用户 ID，
 * 不满足抛 {@link BaseException(FORBIDDEN)} 拒绝。租户条件由
 * {@code TenantLineHandler} 在 SQL 层自动隔离。
 */
@RestController
@RequestMapping("/notify/messages")
public class NotifyController {

    private static final Logger log = LoggerFactory.getLogger(NotifyController.class);

    private final NotifyMessageService notifyMessageService;

    public NotifyController(NotifyMessageService notifyMessageService) {
        this.notifyMessageService = notifyMessageService;
    }

    /**
     * 当前用户的通知列表。
     * <p>
     * 按 {@code recipient_id = 当前 userId} 查询，结果按创建时间倒序。
     * 租户条件由 {@code TenantLineHandler} 自动注入，不手写 tenant 条件。
     * </p>
     *
     * @return 通知列表（可能为空）
     */
    @GetMapping
    public R<List<NotifyMessage>> messages() {
        LoginUser loginUser = LoginUserHolder.get();
        Long currentUserId = loginUser.getUserId();

        // 租户条件由 TenantLineHandler 自动注入，只手写 recipient_id 条件
        List<NotifyMessage> list = notifyMessageService.findByRecipient(currentUserId);

        log.debug("通知列表查询: userId={}, count={}", currentUserId, list.size());
        return R.ok(list);
    }

    /**
     * 标记通知为已读。
     * <p>
     * 前置越权校验：消息的 {@code recipient_id} 必须等于当前用户 ID。
     * 租户条件由 {@code TenantLineHandler} 在 SQL 层自动隔离（查不到等同于 "够不到"）。
     * </p>
     *
     * @param id 通知 ID
     * @return 操作成功
     * @throws BaseException 通知不存在 / 越权时抛出
     */
    @PostMapping("/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        LoginUser loginUser = LoginUserHolder.get();
        Long currentUserId = loginUser.getUserId();

        // 查询消息（租户条件由 TenantLineHandler 自动注入）
        NotifyMessage msg = notifyMessageService.getById(id);
        if (msg == null) {
            throw new BaseException(CommonErrorCode.NOT_FOUND.getCode(), "通知不存在");
        }

        // 越权校验：recipient 归属（同租户下，只能操作自己的通知）
        if (!currentUserId.equals(msg.getRecipientId())) {
            log.warn("越权拒绝（通知收件人不匹配）: msgId={}, msgRecipientId={}, currentUserId={}",
                    id, msg.getRecipientId(), currentUserId);
            throw new BaseException(CommonErrorCode.FORBIDDEN.getCode(), "无权操作该通知");
        }

        msg.setRead(true);
        notifyMessageService.updateById(msg);
        log.debug("通知已标记已读: id={}, recipientId={}", id, currentUserId);

        return R.ok();
    }
}

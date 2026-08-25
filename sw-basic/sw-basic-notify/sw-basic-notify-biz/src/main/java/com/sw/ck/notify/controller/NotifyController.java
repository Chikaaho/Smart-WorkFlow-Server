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
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知消息接收侧控制器（M05 Step 3 — 第四环闭合）。
 * <p>
 * 提供以下接口：
 * <ul>
 *   <li>{@code GET /notify/messages} — 当前用户的未读/已读通知列表，支持已读状态和关键词过滤</li>
 *   <li>{@code POST /notify/messages/{id}/read} — 标记已读（带越权校验）</li>
 *   <li>{@code DELETE /notify/messages/{id} = 删除通知（带越权校验，逻辑删除）</li>
 * </ul>
 * </p>
 *
 * <h3>鉴权</h3>
 * 所有接口均需登录（默认走过滤器链，无需加 permit 白名单）。
 *
 * <h3>越权</h3>
 * {@code read} / {@code delete} 前置校验消息 {@code recipient_id} 必须等于当前用户 ID，
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
     * 当前用户的通知列表（支持过滤）。
     * <p>
     * 按 {@code recipient_id = 当前 userId} 查询，结果按创建时间倒序。
     * 租户条件由 {@code TenantLineHandler} 自动注入，不手写 tenant 条件。
     * </p>
     *
     * @param read    已读状态过滤（可选，null = 不过滤；true = 仅已读；false = 仅未读）
     * @param keyword 关键词过滤（可选，匹配标题或内容）
     * @return 通知列表（可能为空）
     */
    @GetMapping
    public R<List<NotifyMessage>> messages(
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) String keyword) {
        LoginUser loginUser = LoginUserHolder.get();
        Long currentUserId = loginUser.getUserId();

        // 租户条件由 TenantLineHandler 自动注入，只手写 recipient_id 条件
        List<NotifyMessage> list = notifyMessageService.findByRecipientWithFilter(
                currentUserId, read, keyword);

        log.debug("通知列表查询: userId={}, read={}, keyword={}, count={}",
                currentUserId, read, keyword, list.size());
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

    /**
     * 删除通知（逻辑删除）。
     * <p>
     * 前置越权校验：消息的 {@code recipient_id} 必须等于当前用户 ID，
     * 即只允许删除自己收件箱中的通知。
     * 租户条件由 {@code TenantLineHandler} 在 SQL 层自动隔离。
     * </p>
     *
     * @param id 通知 ID
     * @return 操作成功
     * @throws BaseException 通知不存在 / 越权时抛出
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        LoginUser loginUser = LoginUserHolder.get();
        Long currentUserId = loginUser.getUserId();

        // 查询消息（租户条件由 TenantLineHandler 自动注入）
        NotifyMessage msg = notifyMessageService.getById(id);
        if (msg == null) {
            throw new BaseException(CommonErrorCode.NOT_FOUND.getCode(), "通知不存在");
        }

        // 越权校验：recipient 归属（同租户下，只能删除自己的通知）
        if (!currentUserId.equals(msg.getRecipientId())) {
            log.warn("越权拒绝（通知收件人不匹配）: msgId={}, msgRecipientId={}, currentUserId={}",
                    id, msg.getRecipientId(), currentUserId);
            throw new BaseException(CommonErrorCode.FORBIDDEN.getCode(), "无权操作该通知");
        }

        notifyMessageService.deleteMessage(id);
        log.debug("通知已删除: id={}, recipientId={}", id, currentUserId);

        return R.ok();
    }
}

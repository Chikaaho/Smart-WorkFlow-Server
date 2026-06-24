package com.sw.ck.form.dynamic;

import java.security.SecureRandom;

/**
 * NanoId 生成器。
 * <p>
 * 生成符合 {@code [a-z][a-z0-9]{0,11}} 规则的短标识符，
 * 用于动态宽表的物理表名后缀。
 * </p>
 *
 * <p>字符集约束：首位强制小写字母，其余字母或数字，总长 ≤ 12。</p>
 */
public final class NanoIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 首字符字符集：a-z */
    private static final String FIRST = "abcdefghijklmnopqrstuvwxyz";

    /** 后续字符字符集：a-z + 0-9 */
    private static final String REST = "abcdefghijklmnopqrstuvwxyz0123456789";

    /** 默认长度 */
    private static final int DEFAULT_LENGTH = 10;

    /** 最大长度（含首字符） */
    private static final int MAX_LENGTH = 12;

    private NanoIdGenerator() {
        // utility class
    }

    /**
     * 生成长度为 10 的 nanoId。
     *
     * @return 符合 {@code [a-z][a-z0-9]{9}} 的字符串
     */
    public static String generate() {
        return generate(DEFAULT_LENGTH);
    }

    /**
     * 生成指定长度的 nanoId。
     *
     * @param length 总长度（1–12）
     * @return 符合 {@code [a-z][a-z0-9]{n-1}} 的字符串
     */
    public static String generate(int length) {
        if (length < 1 || length > MAX_LENGTH) {
            throw new IllegalArgumentException("NanoId length must be 1–" + MAX_LENGTH + ", got: " + length);
        }

        char[] buf = new char[length];
        buf[0] = FIRST.charAt(RANDOM.nextInt(FIRST.length()));
        for (int i = 1; i < length; i++) {
            buf[i] = REST.charAt(RANDOM.nextInt(REST.length()));
        }

        String id = new String(buf);
        assert id.matches("^[a-z][a-z0-9]{" + (length - 1) + "}$")
                : "Generated nanoId failed pattern validation: " + id;
        return id;
    }
}

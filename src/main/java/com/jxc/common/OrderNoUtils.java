package com.jxc.common;

/**
 * 单号生成工具：前缀 + yyyyMMdd + 3 位序号（不足补 0，超过 999 自然扩展位数）。
 *
 * <p>使用固定零填充，保证「按单号字符串倒序」等价于「按序号倒序」，
 * 因此不会再出现 substring 取末 3 位导致的序号回绕。</p>
 */
public final class OrderNoUtils {

    private OrderNoUtils() {
    }

    /**
     * @param prefix   单号前缀，如 CG / XS / PD
     * @param dateStr  日期串，如 20250926
     * @param lastNo   当天已有的最大单号，可为 null
     * @return 下一个单号
     */
    public static String next(String prefix, String dateStr, String lastNo) {
        String head = prefix + dateStr;
        int seq = 1;
        if (lastNo != null && lastNo.length() > head.length()) {
            try {
                seq = Integer.parseInt(lastNo.substring(head.length())) + 1;
            } catch (NumberFormatException ignored) {
                seq = 1;
            }
        }
        return head + String.format("%03d", seq);
    }
}

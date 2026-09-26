package com.jxc.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 人民币金额大写转换（送货单「金额(大写)」栏使用）。
 *
 * <p>例：278 → 贰佰柒拾捌元整；1234.56 → 壹仟贰佰叁拾肆元伍角陆分；100000005 → 壹亿零伍元整。</p>
 */
public final class MoneyUpper {

    private static final char[] DIGITS = "零壹贰叁肆伍陆柒捌玖".toCharArray();
    private static final String[] UNITS = {"", "拾", "佰", "仟"};
    private static final String[] SECTIONS = {"", "万", "亿", "万亿"};

    private MoneyUpper() {
    }

    public static String toUpper(BigDecimal amount) {
        if (amount == null) {
            return "零元整";
        }
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        boolean negative = value.signum() < 0;
        value = value.abs();
        long yuan = value.longValue();
        int cents = value.subtract(new BigDecimal(yuan)).movePointRight(2).intValue();
        int jiao = cents / 10;
        int fen = cents % 10;

        StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append('负');
        }
        sb.append(upperYuan(yuan));
        if (jiao == 0 && fen == 0) {
            sb.append("整");
        } else {
            if (jiao > 0) {
                sb.append(DIGITS[jiao]).append('角');
            } else if (yuan > 0) {
                // 如 壹佰元零伍分
                sb.append('零');
            }
            if (fen > 0) {
                sb.append(DIGITS[fen]).append('分');
            }
        }
        return sb.toString();
    }

    private static String upperYuan(long yuan) {
        if (yuan == 0) {
            return "零元";
        }
        // 按万进制拆节，parts[i] 为第 i 节（0=个级，1=万级，2=亿级）的文本
        String[] parts = new String[SECTIONS.length];
        int[] values = new int[SECTIONS.length];
        int index = 0;
        long rest = yuan;
        while (rest > 0 && index < SECTIONS.length) {
            int section = (int) (rest % 10000);
            values[index] = section;
            parts[index] = section == 0 ? "" : sectionToUpper(section) + SECTIONS[index];
            rest /= 10000;
            index++;
        }

        StringBuilder sb = new StringBuilder();
        boolean zeroBetween = false;
        for (int i = index - 1; i >= 0; i--) {
            if (parts[i].isEmpty()) {
                zeroBetween = sb.length() > 0;
                continue;
            }
            if (sb.length() > 0 && (zeroBetween || values[i] < 1000)) {
                sb.append('零');
            }
            sb.append(parts[i]);
            zeroBetween = false;
        }
        return sb.append('元').toString();
    }

    /** 4 位以内小节转大写，如 1005 → 壹仟零伍 */
    private static String sectionToUpper(int section) {
        StringBuilder sb = new StringBuilder();
        boolean pendingZero = false;
        for (int unit = 3; unit >= 0; unit--) {
            int divisor = (int) Math.pow(10, unit);
            int digit = (section / divisor) % 10;
            if (digit == 0) {
                if (sb.length() > 0) {
                    pendingZero = true;
                }
            } else {
                if (pendingZero) {
                    sb.append('零');
                    pendingZero = false;
                }
                sb.append(DIGITS[digit]).append(UNITS[unit]);
            }
        }
        return sb.toString();
    }
}

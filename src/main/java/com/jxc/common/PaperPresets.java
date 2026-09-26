package com.jxc.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 送货单纸张预设（后端直连打印用）。
 *
 * <p>尺寸单位毫米，边距已为针式打印机预留左侧不可打印区。</p>
 */
public final class PaperPresets {

    /** 1 毫米对应的点数（1pt = 1/72 inch） */
    public static final double MM_TO_PT = 72.0 / 25.4;

    public record Preset(String key, String label,
                         double widthMm, double heightMm,
                         double marginLeftMm, double marginRightMm,
                         double marginTopMm, double marginBottomMm,
                         boolean dotMatrix) {

        public double widthPt() {
            return widthMm * MM_TO_PT;
        }

        public double heightPt() {
            return heightMm * MM_TO_PT;
        }

        public double imageableWidthPt() {
            return (widthMm - marginLeftMm - marginRightMm) * MM_TO_PT;
        }

        public double imageableHeightPt() {
            return (heightMm - marginTopMm - marginBottomMm) * MM_TO_PT;
        }
    }

    public static final List<Preset> ALL = List.of(
            new Preset("dot-241-140", "针式 241×140", 241, 140, 8, 6, 6, 6, true),
            new Preset("dot-241-93", "针式 241×93", 241, 93, 8, 6, 5, 5, true),
            new Preset("dot-210-140", "针式 210×140", 210, 140, 7, 6, 6, 6, true),
            new Preset("a5-landscape", "普通 A5 横向", 210, 148, 10, 10, 8, 8, false),
            new Preset("a4-landscape", "普通 A4 横向", 297, 210, 12, 12, 10, 10, false)
    );

    public static final String DEFAULT_KEY = "dot-241-140";

    private PaperPresets() {
    }

    public static Preset of(String key) {
        if (key != null) {
            for (Preset preset : ALL) {
                if (preset.key().equalsIgnoreCase(key.trim())) {
                    return preset;
                }
            }
        }
        return ALL.get(0);
    }

    public static boolean isValid(String key) {
        if (key == null) {
            return false;
        }
        for (Preset preset : ALL) {
            if (preset.key().equalsIgnoreCase(key.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 供前端下拉使用：key + 中文名 + 是否针式 */
    public static List<Map<String, Object>> options() {
        return ALL.stream().map(preset -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("value", preset.key());
            map.put("label", preset.label());
            map.put("dotMatrix", preset.dotMatrix());
            map.put("size", String.format("%.0fmm×%.0fmm", preset.widthMm(), preset.heightMm()));
            return map;
        }).toList();
    }
}

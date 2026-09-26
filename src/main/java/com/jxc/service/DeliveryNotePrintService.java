package com.jxc.service;

import java.util.Map;

/**
 * 送货单后端直连打印（静默，不经过浏览器）。
 */
public interface DeliveryNotePrintService {

    /** 可用打印机列表 + 纸张选项 + 当前打印配置 */
    Map<String, Object> printerInfo();

    /**
     * 直接打印送货单。
     *
     * @param printerName 打印机名，为空则用配置值，再为空则用系统默认打印机
     * @param paperKey    纸张预设 key（见 PaperPresets），为空则用配置值
     * @param copies      份数，为空则用配置值（针式复写纸一般 1 份）
     */
    Map<String, Object> printDeliveryNote(Long orderId, String printerName, String paperKey, Integer copies);
}

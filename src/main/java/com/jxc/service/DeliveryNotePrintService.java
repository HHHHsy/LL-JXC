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

    /**
     * 打印标定测试页：已知坐标的刻度网格（每 10mm），用于实测驱动/打印机的真实原点偏移。
     *
     * @param printerName 打印机名，为空则用配置值
     */
    Map<String, Object> printTestPage(String printerName);

    /**
     * 调试：导出发往打印机的光栅图（PNG），用于肉眼核对渲染结果。
     *
     * @param type    note=送货单 / test=标定页
     * @param orderId 送货单需要订单 id
     */
    byte[] debugRaster(String type, Long orderId) throws Exception;
}

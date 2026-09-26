package com.jxc.common;

/**
 * 订单/单据状态常量（集中定义，避免各处硬编码字符串不一致）。
 */
public final class OrderStatus {

    /* 采购 */
    public static final String PURCHASE_PENDING = "待入库";
    public static final String PURCHASE_PARTIAL = "部分入库";
    public static final String PURCHASE_DONE = "已入库";

    /* 销售 */
    public static final String SALE_PENDING = "待出库";
    public static final String SALE_PARTIAL = "部分出库";
    public static final String SALE_DONE = "已出库";

    /* 通用 */
    public static final String CANCELED = "已取消";

    private OrderStatus() {
    }
}

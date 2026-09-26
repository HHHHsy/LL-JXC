package com.jxc.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 送货单表格中的一行（对应销售明细）。
 */
@Data
public class DeliveryNoteItem {

    /** 序号（从 1 开始） */
    private int seq;

    private String productName;

    /** 型号规格 */
    private String spec;

    private Integer quantity;

    private String unit;

    /** 单价 */
    private BigDecimal price;

    /** 金额 = 数量 × 单价 */
    private BigDecimal amount;

    /** 备注（取自销售订单明细的备注字段，如：正装/配件） */
    private String remark = "";

    /** 空行占位（为了与纸质单据的行数对齐） */
    private boolean blank;
}

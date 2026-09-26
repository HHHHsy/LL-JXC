package com.jxc.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购清单导入结果。
 */
@Data
public class PurchaseImportResult {

    private Long orderId;
    private String orderNo;
    private int detailCount;
    private int createdProductCount;
    private int skippedRowCount;
    private int totalQuantity;
    private BigDecimal totalAmount = BigDecimal.ZERO;
}

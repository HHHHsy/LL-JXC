package com.jxc.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购明细报表行（由 ReportMapper 的 SQL 直接映射）。
 */
@Data
public class PurchaseReportRow {

    private LocalDate purchaseDate;
    private String orderNo;
    private String supplierName;
    private String productName;
    private Integer quantity;
    private Integer receivedQuantity;
    private BigDecimal costPrice;
    private BigDecimal subtotal;
}

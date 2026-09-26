package com.jxc.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售明细报表行（由 ReportMapper 的 SQL 直接映射）。
 */
@Data
public class SaleReportRow {

    private LocalDate saleDate;
    private String orderNo;
    private String customerName;
    private String productName;
    private Integer quantity;
    private Integer shippedQuantity;
    private BigDecimal salePrice;
    private BigDecimal subtotal;
}

package com.jxc.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售送货单打印数据。
 */
@Data
public class DeliveryNote {

    /* 抬头（可在 application.yml 的 jxc.delivery-note 下配置） */
    private String companyName;
    private String servicePhone;
    private String serviceAddress;
    private String deliveryMan;
    private String footerNote;
    private String title = "送货单";

    /* 单据信息 */
    private String orderNo;
    private LocalDate saleDate;
    private String customerName;
    private String customerAddress;
    private String customerPhone;
    private String orderStatus;

    /* 明细与合计 */
    private List<DeliveryNoteItem> items = new ArrayList<>();
    private int totalQuantity;
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** 金额大写 */
    private String totalAmountUpper;
}

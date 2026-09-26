package com.jxc.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购清单 Excel 导入解析出来的一行。
 */
@Data
public class PurchaseImportRow {

    /** Excel 中的物理行号（从 1 开始，便于用户定位） */
    private int rowNo;

    /** 清单里的「序号」列，可为空 */
    private String seq;

    /** 采购型号名称（必填） */
    private String productName;

    /** 参考规格型号 */
    private String spec;

    /** 单位 */
    private String unit;

    /** 数量 */
    private Integer quantity;

    /** 单价（元） */
    private BigDecimal unitPrice;

    /** 清单中的金额（元），仅用于核对 */
    private BigDecimal amount;

    /** 参考适合车型 */
    private String carModels;

    /** 匹配到的已有商品ID（为空表示需要新建） */
    private Long matchedProductId;

    /** 匹配到的已有商品编码 */
    private String matchedProductCode;

    /** 状态：OK=匹配到已有商品，NEW=将新建商品，ERROR=该行有问题 */
    private String status;

    /** 状态说明（错误原因或提示） */
    private String message;

    /** 金额与「数量×单价」是否不一致 */
    private boolean amountMismatch;
}

package com.jxc.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品导入 Excel 解析出来的一行。
 */
@Data
public class ProductImportRow {

    /** Excel 物理行号（从 1 开始） */
    private int rowNo;

    /** 商品编码（可空，空则自动生成） */
    private String code;

    /** 商品名称（必填） */
    private String name;

    /** 商品分类 */
    private String category;

    /** 规格型号 */
    private String spec;

    /** 单位 */
    private String unit;

    /** 成本价 */
    private BigDecimal costPrice;

    /** 销售价 */
    private BigDecimal salePrice;

    /** 状态：NEW=可新增，ERROR=该行有问题 */
    private String status;

    /** 状态说明（错误原因或提示） */
    private String message;
}

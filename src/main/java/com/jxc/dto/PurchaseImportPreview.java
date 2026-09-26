package com.jxc.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购清单导入的解析/预览结果。
 */
@Data
public class PurchaseImportPreview {

    private String fileName;
    private String sheetName;

    /** Excel 总行数（含表头与汇总行） */
    private int totalRows;

    /** 识别为数据行并参与导入的行数 */
    private int dataRows;

    /** 被跳过的行数（空行、汇总行等） */
    private int skippedRows;

    /** 匹配到已有商品的行数 */
    private int matchedRows;

    /** 需要新建商品的行数 */
    private int newProductRows;

    /** 有问题的行数 */
    private int errorRows;

    /** 可导入的合计数量 */
    private int totalQuantity;

    /** 可导入的合计金额 */
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** 明细行 */
    private List<PurchaseImportRow> rows = new ArrayList<>();

    /** 跳过的行说明 */
    private List<String> skipped = new ArrayList<>();

    /** 是否可以直接导入（无错误行） */
    private boolean importable;
}

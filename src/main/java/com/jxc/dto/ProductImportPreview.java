package com.jxc.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品导入的解析/预览结果。
 */
@Data
public class ProductImportPreview {

    private String fileName;
    private String sheetName;

    /** 表格总行数（含表头） */
    private int totalRows;

    /** 识别为数据行并参与导入的行数 */
    private int dataRows;

    /** 被跳过的行数（空行、汇总行等） */
    private int skippedRows;

    /** 可新增的行数 */
    private int newRows;

    /** 有问题的行数 */
    private int errorRows;

    private List<ProductImportRow> rows = new ArrayList<>();

    private List<String> skipped = new ArrayList<>();

    /** 无错误行时为 true */
    private boolean importable;
}

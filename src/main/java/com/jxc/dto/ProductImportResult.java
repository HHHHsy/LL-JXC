package com.jxc.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品导入结果。
 */
@Data
public class ProductImportResult {

    /** 成功新增的商品数 */
    private int createdCount;

    /** 跳过的行数（空行 + 用户选择忽略的问题行） */
    private int skippedRowCount;

    /** 新增商品的编码清单（便于用户核对/打印） */
    private List<String> createdCodes = new ArrayList<>();
}

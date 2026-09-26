package com.jxc.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 出入库提交的一行：本次入库/出库数量。
 */
@Data
public class StockMoveItem {

    /** 采购/销售明细ID（推荐传，用于精确匹配；不传则按商品ID匹配） */
    private Long id;

    @NotNull(message = "明细缺少商品")
    private Long productId;

    @NotNull(message = "本次数量不能为空")
    @Min(value = 1, message = "本次数量必须大于0")
    private Integer quantity;
}

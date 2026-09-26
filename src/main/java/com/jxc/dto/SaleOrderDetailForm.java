package com.jxc.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售订单明细请求字段。
 */
@Data
public class SaleOrderDetailForm {

    private Long id;

    @NotNull(message = "请选择商品")
    private Long productId;

    @NotNull(message = "销售数量不能为空")
    @Min(value = 1, message = "销售数量必须大于0")
    private Integer quantity;

    @Min(value = 0, message = "销售价不能为负数")
    private BigDecimal salePrice;

    /** 备注（打印在送货单的「备注」列，如：正装） */
    @Size(max = 200, message = "备注长度不能超过200个字符")
    private String remark;

    private Integer shippedQuantity;
}

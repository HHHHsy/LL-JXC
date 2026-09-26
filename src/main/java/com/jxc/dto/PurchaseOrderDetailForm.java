package com.jxc.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购订单明细请求字段。
 */
@Data
public class PurchaseOrderDetailForm {

    private Long id;

    @NotNull(message = "请选择商品")
    private Long productId;

    @NotNull(message = "采购数量不能为空")
    @Min(value = 1, message = "采购数量必须大于0")
    private Integer quantity;

    @Min(value = 0, message = "成本价不能为负数")
    private BigDecimal costPrice;

    /** 备注（如：参考适合车型；Excel 导入会写入此列，编辑订单时需回传以免丢失） */
    @Size(max = 200, message = "备注长度不能超过200个字符")
    private String remark;

    private Integer receivedQuantity;
}

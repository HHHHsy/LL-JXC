package com.jxc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sale_order_detail")
public class SaleOrderDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private Integer shippedQuantity;
    private BigDecimal salePrice;
    /** 备注（打印在送货单的「备注」列） */
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Integer getShippedQuantity() {
        return shippedQuantity == null ? 0 : shippedQuantity;
    }
}

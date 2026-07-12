package com.jxc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("inventory_log")
public class InventoryLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDateTime changeTime;
    private Long productId;
    private String changeType;
    private Integer changeQuantity;
    private Integer afterStock;
    private String relatedOrderNo;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

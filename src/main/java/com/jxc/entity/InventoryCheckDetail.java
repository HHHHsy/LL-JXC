package com.jxc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("inventory_check_detail")
public class InventoryCheckDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long checkId;
    private Long productId;
    private Integer systemQuantity;
    private Integer actualQuantity;
    private Integer diffQuantity;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

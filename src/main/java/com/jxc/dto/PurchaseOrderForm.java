package com.jxc.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 采购订单主表请求字段。
 */
@Data
public class PurchaseOrderForm {

    /** 修改时必传 */
    private Long id;

    @NotNull(message = "请选择供应商")
    private Long supplierId;

    private LocalDate purchaseDate;

    /** 仅用于兼容前端回传，服务端会强制为「待入库」 */
    private String status;
}

package com.jxc.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 销售订单主表请求字段。
 */
@Data
public class SaleOrderForm {

    /** 修改时必传 */
    private Long id;

    @NotNull(message = "请选择客户")
    private Long customerId;

    private LocalDate saleDate;

    /** 仅用于兼容前端回传，服务端会强制为「待出库」 */
    private String status;
}

package com.jxc.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 销售订单新增/修改请求体：{ order: {...}, details: [...] }
 */
@Data
public class SaleOrderSaveRequest {

    @NotNull(message = "缺少订单信息")
    @Valid
    private SaleOrderForm order;

    @NotEmpty(message = "请至少录入一条销售明细")
    @Valid
    private List<SaleOrderDetailForm> details;
}

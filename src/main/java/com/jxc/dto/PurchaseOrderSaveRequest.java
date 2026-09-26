package com.jxc.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 采购订单新增/修改请求体：{ order: {...}, details: [...] }
 */
@Data
public class PurchaseOrderSaveRequest {

    @NotNull(message = "缺少订单信息")
    @Valid
    private PurchaseOrderForm order;

    @NotEmpty(message = "请至少录入一条采购明细")
    @Valid
    private List<PurchaseOrderDetailForm> details;
}

package com.jxc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jxc.common.Result;
import com.jxc.entity.PurchaseOrder;
import com.jxc.entity.PurchaseOrderDetail;

import java.util.List;

public interface PurchaseOrderService extends IService<PurchaseOrder> {

    Result createOrder(PurchaseOrder order, List<PurchaseOrderDetail> details);

    Result updateOrder(PurchaseOrder order, List<PurchaseOrderDetail> details);

    Result receiveStock(Long orderId, List<PurchaseOrderDetail> details);
}

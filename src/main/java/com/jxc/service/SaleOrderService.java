package com.jxc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jxc.common.Result;
import com.jxc.entity.SaleOrder;
import com.jxc.entity.SaleOrderDetail;

import java.util.List;

public interface SaleOrderService extends IService<SaleOrder> {

    Result createOrder(SaleOrder order, List<SaleOrderDetail> details);

    Result updateOrder(SaleOrder order, List<SaleOrderDetail> details);

    Result shipStock(Long orderId, List<SaleOrderDetail> details);

    /** 取消订单（仅待出库状态允许） */
    Result cancelOrder(Long orderId);
}

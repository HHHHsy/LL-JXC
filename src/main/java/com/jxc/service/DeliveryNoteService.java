package com.jxc.service;

import com.jxc.dto.DeliveryNote;

/**
 * 送货单打印数据。
 */
public interface DeliveryNoteService {

    /** 根据销售订单生成送货单数据（含客户信息、明细、合计与金额大写） */
    DeliveryNote buildForSaleOrder(Long orderId);
}

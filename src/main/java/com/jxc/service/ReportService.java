package com.jxc.service;

import com.jxc.dto.PurchaseReportRow;
import com.jxc.dto.ReportResult;
import com.jxc.dto.SaleReportRow;

/**
 * 统计报表查询。
 */
public interface ReportService {

    /**
     * 采购明细表。
     *
     * @param status 可选：按订单状态过滤（不传=全部；已取消的订单始终不计入）
     */
    ReportResult<PurchaseReportRow> purchaseDetail(String startDate, String endDate, Long supplierId,
                                                   String status, long pageNum, long pageSize);

    ReportResult<SaleReportRow> saleDetail(String startDate, String endDate, Long customerId,
                                           String status, long pageNum, long pageSize);
}

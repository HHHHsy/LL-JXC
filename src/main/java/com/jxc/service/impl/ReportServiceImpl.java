package com.jxc.service.impl;

import com.jxc.dto.PurchaseReportRow;
import com.jxc.dto.ReportResult;
import com.jxc.dto.SaleReportRow;
import com.jxc.mapper.ReportMapper;
import com.jxc.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private ReportMapper reportMapper;

    @Override
    public ReportResult<PurchaseReportRow> purchaseDetail(String startDate, String endDate, Long supplierId,
                                                          String status, long pageNum, long pageSize) {
        ReportResult<PurchaseReportRow> result = new ReportResult<>();
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setTotal(reportMapper.countPurchaseDetail(startDate, endDate, supplierId, status));
        List<PurchaseReportRow> rows = reportMapper.selectPurchaseDetail(
                startDate, endDate, supplierId, status, offset(pageNum, pageSize), pageSize);
        result.setList(rows);
        applySummary(result, reportMapper.sumPurchaseDetail(startDate, endDate, supplierId, status));
        return result;
    }

    @Override
    public ReportResult<SaleReportRow> saleDetail(String startDate, String endDate, Long customerId,
                                                  String status, long pageNum, long pageSize) {
        ReportResult<SaleReportRow> result = new ReportResult<>();
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setTotal(reportMapper.countSaleDetail(startDate, endDate, customerId, status));
        List<SaleReportRow> rows = reportMapper.selectSaleDetail(
                startDate, endDate, customerId, status, offset(pageNum, pageSize), pageSize);
        result.setList(rows);
        applySummary(result, reportMapper.sumSaleDetail(startDate, endDate, customerId, status));
        return result;
    }

    private long offset(long pageNum, long pageSize) {
        return Math.max(0, (Math.max(1, pageNum) - 1) * Math.max(1, pageSize));
    }

    private void applySummary(ReportResult<?> result, Map<String, Object> summary) {
        if (summary == null) {
            return;
        }
        result.setTotalQuantity(toDecimal(summary.get("totalQuantity")));
        result.setTotalAmount(toDecimal(summary.get("totalAmount")));
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }
}

package com.jxc.controller;

import com.jxc.common.Result;
import com.jxc.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计报表。聚合计算已下推到 Mapper 的 SQL，控制器只负责参数与返回。
 */
@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/purchase-detail")
    public Result purchaseDetail(@RequestParam(required = false) String startDate,
                                 @RequestParam(required = false) String endDate,
                                 @RequestParam(required = false) Long supplierId,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(defaultValue = "1") long pageNum,
                                 @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(reportService.purchaseDetail(startDate, endDate, supplierId, status, pageNum, pageSize));
    }

    @GetMapping("/sale-detail")
    public Result saleDetail(@RequestParam(required = false) String startDate,
                             @RequestParam(required = false) String endDate,
                             @RequestParam(required = false) Long customerId,
                             @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "1") long pageNum,
                             @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(reportService.saleDetail(startDate, endDate, customerId, status, pageNum, pageSize));
    }
}

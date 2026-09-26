package com.jxc.controller;

import com.jxc.common.Result;
import com.jxc.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页看板。统计口径与聚合逻辑在 DashboardService / DashboardMapper 中。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/stats")
    public Result stats() {
        return Result.success(dashboardService.stats());
    }
}

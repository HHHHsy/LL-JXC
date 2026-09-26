package com.jxc.service.impl;

import com.jxc.mapper.DashboardMapper;
import com.jxc.service.CustomerService;
import com.jxc.service.DashboardService;
import com.jxc.service.ProductService;
import com.jxc.service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板统计实现：数据聚合全部由 Mapper 的 SQL 完成（分组、求和、Top N），
 * 服务层只做「补齐近 6 个月空档」这类呈现层处理，避免把全表数据捞进内存。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int TREND_MONTHS = 6;
    private static final int RECENT_LOG_LIMIT = 10;
    private static final int TOP_PRODUCT_LIMIT = 5;

    @Autowired
    private DashboardMapper dashboardMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private SupplierService supplierService;

    @Value("${jxc.low-stock-threshold:10}")
    private int lowStockThreshold = 10;

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("productCount", productService.count());
        data.put("customerCount", customerService.count());
        data.put("supplierCount", supplierService.count());
        data.put("lowStockCount", dashboardMapper.countLowStock(lowStockThreshold));
        data.put("lowStockThreshold", lowStockThreshold);

        data.put("purchaseTrend", fillMonths(dashboardMapper.purchaseTrend(firstMonthOfTrend())));
        data.put("saleTrend", fillMonths(dashboardMapper.saleTrend(firstMonthOfTrend())));
        data.put("categoryDistribution", dashboardMapper.categoryDistribution());
        data.put("recentLogs", dashboardMapper.recentLogs(RECENT_LOG_LIMIT));
        data.put("topProducts", dashboardMapper.topSaleProducts(LocalDate.now().format(MONTH), TOP_PRODUCT_LIMIT));
        return data;
    }

    private String firstMonthOfTrend() {
        return LocalDate.now().minusMonths(TREND_MONTHS - 1).withDayOfMonth(1).format(MONTH) + "-01";
    }

    /** 把 SQL 返回的稀疏月份补齐成连续 N 个月，缺的补 0 */
    private List<Map<String, Object>> fillMonths(List<Map<String, Object>> rows) {
        Map<String, Object> byMonth = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object month = row.get("month");
                if (month != null) {
                    byMonth.put(String.valueOf(month), row.get("amount"));
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = TREND_MONTHS - 1; i >= 0; i--) {
            String month = LocalDate.now().minusMonths(i).format(MONTH);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", month);
            Object amount = byMonth.get(month);
            item.put("amount", amount == null ? BigDecimal.ZERO : amount);
            result.add(item);
        }
        return result;
    }
}

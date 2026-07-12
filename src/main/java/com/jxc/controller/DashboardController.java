package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.Result;
import com.jxc.entity.*;
import com.jxc.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private ProductService productService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    @Autowired
    private PurchaseOrderDetailService purchaseOrderDetailService;
    @Autowired
    private SaleOrderService saleOrderService;
    @Autowired
    private SaleOrderDetailService saleOrderDetailService;
    @Autowired
    private InventoryLogService inventoryLogService;

    @GetMapping("/stats")
    public Result stats() {
        Map<String, Object> data = new HashMap<>();

        // 汇总卡片
        data.put("productCount", productService.count());
        data.put("customerCount", customerService.count());
        data.put("supplierCount", supplierService.count());
        QueryWrapper<Product> lowStockWrapper = new QueryWrapper<>();
        lowStockWrapper.lt("stock_quantity", 10);
        data.put("lowStockCount", productService.count(lowStockWrapper));

        // 近6个月采购趋势
        data.put("purchaseTrend", getMonthlyPurchaseTrend());

        // 近6个月销售趋势
        data.put("saleTrend", getMonthlySaleTrend());

        // 商品分类分布
        data.put("categoryDistribution", getCategoryDistribution());

        // 最近出入库流水
        QueryWrapper<InventoryLog> logWrapper = new QueryWrapper<>();
        logWrapper.orderByDesc("change_time");
        logWrapper.last("limit 10");
        List<InventoryLog> logs = inventoryLogService.list(logWrapper);
        List<Map<String, Object>> logList = new ArrayList<>();
        for (InventoryLog log : logs) {
            Map<String, Object> m = new HashMap<>();
            m.put("changeTime", log.getChangeTime());
            m.put("changeType", log.getChangeType());
            m.put("changeQuantity", log.getChangeQuantity());
            m.put("relatedOrderNo", log.getRelatedOrderNo());
            m.put("remark", log.getRemark());
            Product p = productService.getById(log.getProductId());
            m.put("productName", p != null ? p.getName() : "");
            logList.add(m);
        }
        data.put("recentLogs", logList);

        // 本月热销商品Top5
        data.put("topProducts", getTopSaleProducts());

        return Result.success(data);
    }

    private List<Map<String, Object>> getMonthlyPurchaseTrend() {
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 5; i >= 0; i--) {
            String month = LocalDate.now().minusMonths(i).format(fmt);
            QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<>();
            wrapper.eq("status", "已入库");
            wrapper.likeRight("purchase_date", month);
            List<PurchaseOrder> orders = purchaseOrderService.list(wrapper);
            BigDecimal total = BigDecimal.ZERO;
            if (!orders.isEmpty()) {
                List<Long> ids = orders.stream().map(PurchaseOrder::getId).collect(Collectors.toList());
                QueryWrapper<PurchaseOrderDetail> dw = new QueryWrapper<>();
                dw.in("order_id", ids);
                List<PurchaseOrderDetail> details = purchaseOrderDetailService.list(dw);
                for (PurchaseOrderDetail d : details) {
                    total = total.add(d.getCostPrice().multiply(new BigDecimal(d.getQuantity())));
                }
            }
            Map<String, Object> m = new HashMap<>();
            m.put("month", month);
            m.put("amount", total);
            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> getMonthlySaleTrend() {
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 5; i >= 0; i--) {
            String month = LocalDate.now().minusMonths(i).format(fmt);
            QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
            wrapper.eq("status", "已出库");
            wrapper.likeRight("sale_date", month);
            List<SaleOrder> orders = saleOrderService.list(wrapper);
            BigDecimal total = BigDecimal.ZERO;
            if (!orders.isEmpty()) {
                List<Long> ids = orders.stream().map(SaleOrder::getId).collect(Collectors.toList());
                QueryWrapper<SaleOrderDetail> dw = new QueryWrapper<>();
                dw.in("order_id", ids);
                List<SaleOrderDetail> details = saleOrderDetailService.list(dw);
                for (SaleOrderDetail d : details) {
                    total = total.add(d.getSalePrice().multiply(new BigDecimal(d.getQuantity())));
                }
            }
            Map<String, Object> m = new HashMap<>();
            m.put("month", month);
            m.put("amount", total);
            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> getCategoryDistribution() {
        List<Product> products = productService.list();
        Map<String, Integer> categoryMap = new HashMap<>();
        for (Product p : products) {
            String cat = p.getCategory() != null && !p.getCategory().isEmpty() ? p.getCategory() : "未分类";
            categoryMap.merge(cat, 1, Integer::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : categoryMap.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", entry.getKey());
            m.put("value", entry.getValue());
            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> getTopSaleProducts() {
        // 本月已出库订单
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "已出库");
        wrapper.likeRight("sale_date", month);
        List<SaleOrder> orders = saleOrderService.list(wrapper);
        if (orders.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> orderIds = orders.stream().map(SaleOrder::getId).collect(Collectors.toList());
        QueryWrapper<SaleOrderDetail> dw = new QueryWrapper<>();
        dw.in("order_id", orderIds);
        List<SaleOrderDetail> details = saleOrderDetailService.list(dw);

        Map<Long, Integer> productQtyMap = new HashMap<>();
        for (SaleOrderDetail d : details) {
            productQtyMap.merge(d.getProductId(), d.getQuantity(), Integer::sum);
        }

        return productQtyMap.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(5)
                .map(entry -> {
                    Product p = productService.getById(entry.getKey());
                    Map<String, Object> m = new HashMap<>();
                    m.put("productName", p != null ? p.getName() : "");
                    m.put("quantity", entry.getValue());
                    return m;
                })
                .collect(Collectors.toList());
    }
}

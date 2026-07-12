package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.Result;
import com.jxc.entity.*;
import com.jxc.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private PurchaseOrderDetailService purchaseOrderDetailService;

    @Autowired
    private SaleOrderService saleOrderService;

    @Autowired
    private SaleOrderDetailService saleOrderDetailService;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @GetMapping("/purchase-detail")
    public Result purchaseDetail(@RequestParam(required = false) String startDate,
                                  @RequestParam(required = false) String endDate,
                                  @RequestParam(required = false) Long supplierId) {
        QueryWrapper<PurchaseOrder> orderWrapper = new QueryWrapper<>();
        if (startDate != null && !startDate.isEmpty()) {
            orderWrapper.ge("purchase_date", startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            orderWrapper.le("purchase_date", endDate);
        }
        if (supplierId != null) {
            orderWrapper.eq("supplier_id", supplierId);
        }
        List<PurchaseOrder> orders = purchaseOrderService.list(orderWrapper);
        if (orders.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("list", new ArrayList<>());
            Map<String, Object> total = new HashMap<>();
            total.put("totalQuantity", 0);
            total.put("totalAmount", BigDecimal.ZERO);
            result.put("total", total);
            return Result.success(result);
        }

        List<Long> orderIds = orders.stream().map(PurchaseOrder::getId).collect(Collectors.toList());
        Map<Long, PurchaseOrder> orderMap = orders.stream()
                .collect(Collectors.toMap(PurchaseOrder::getId, o -> o));

        QueryWrapper<PurchaseOrderDetail> detailWrapper = new QueryWrapper<>();
        detailWrapper.in("order_id", orderIds);
        List<PurchaseOrderDetail> details = purchaseOrderDetailService.list(detailWrapper);

        Set<Long> supplierIds = orders.stream().map(PurchaseOrder::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> productIds = details.stream().map(PurchaseOrderDetail::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> supplierNameMap = new HashMap<>();
        if (!supplierIds.isEmpty()) {
            List<Supplier> suppliers = supplierService.listByIds(supplierIds);
            for (Supplier s : suppliers) {
                supplierNameMap.put(s.getId(), s.getName());
            }
        }

        Map<Long, Product> productMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            List<Product> products = productService.listByIds(productIds);
            for (Product p : products) {
                productMap.put(p.getId(), p);
            }
        }

        List<Map<String, Object>> resultList = new ArrayList<>();
        int totalQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (PurchaseOrderDetail detail : details) {
            PurchaseOrder order = orderMap.get(detail.getOrderId());
            if (order == null) continue;

            Map<String, Object> row = new HashMap<>();
            row.put("purchaseDate", order.getPurchaseDate());
            row.put("orderNo", order.getOrderNo());
            row.put("supplierName", supplierNameMap.getOrDefault(order.getSupplierId(), ""));
            Product product = productMap.get(detail.getProductId());
            row.put("productName", product != null ? product.getName() : "");
            row.put("quantity", detail.getQuantity());
            row.put("costPrice", detail.getCostPrice());
            BigDecimal subtotal = BigDecimal.ZERO;
            if (detail.getQuantity() != null && detail.getCostPrice() != null) {
                subtotal = detail.getCostPrice().multiply(new BigDecimal(detail.getQuantity()));
            }
            row.put("subtotal", subtotal);
            resultList.add(row);

            totalQuantity += detail.getQuantity() != null ? detail.getQuantity() : 0;
            totalAmount = totalAmount.add(subtotal);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultList);
        Map<String, Object> total = new HashMap<>();
        total.put("totalQuantity", totalQuantity);
        total.put("totalAmount", totalAmount);
        result.put("total", total);
        return Result.success(result);
    }

    @GetMapping("/sale-detail")
    public Result saleDetail(@RequestParam(required = false) String startDate,
                              @RequestParam(required = false) String endDate,
                              @RequestParam(required = false) Long customerId) {
        QueryWrapper<SaleOrder> orderWrapper = new QueryWrapper<>();
        if (startDate != null && !startDate.isEmpty()) {
            orderWrapper.ge("sale_date", startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            orderWrapper.le("sale_date", endDate);
        }
        if (customerId != null) {
            orderWrapper.eq("customer_id", customerId);
        }
        List<SaleOrder> orders = saleOrderService.list(orderWrapper);
        if (orders.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("list", new ArrayList<>());
            Map<String, Object> total = new HashMap<>();
            total.put("totalQuantity", 0);
            total.put("totalAmount", BigDecimal.ZERO);
            result.put("total", total);
            return Result.success(result);
        }

        List<Long> orderIds = orders.stream().map(SaleOrder::getId).collect(Collectors.toList());
        Map<Long, SaleOrder> orderMap = orders.stream()
                .collect(Collectors.toMap(SaleOrder::getId, o -> o));

        QueryWrapper<SaleOrderDetail> detailWrapper = new QueryWrapper<>();
        detailWrapper.in("order_id", orderIds);
        List<SaleOrderDetail> details = saleOrderDetailService.list(detailWrapper);

        Set<Long> customerIds = orders.stream().map(SaleOrder::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> productIds = details.stream().map(SaleOrderDetail::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> customerNameMap = new HashMap<>();
        if (!customerIds.isEmpty()) {
            List<Customer> customers = customerService.listByIds(customerIds);
            for (Customer c : customers) {
                customerNameMap.put(c.getId(), c.getName());
            }
        }

        Map<Long, Product> productMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            List<Product> products = productService.listByIds(productIds);
            for (Product p : products) {
                productMap.put(p.getId(), p);
            }
        }

        List<Map<String, Object>> resultList = new ArrayList<>();
        int totalQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (SaleOrderDetail detail : details) {
            SaleOrder order = orderMap.get(detail.getOrderId());
            if (order == null) continue;

            Map<String, Object> row = new HashMap<>();
            row.put("saleDate", order.getSaleDate());
            row.put("orderNo", order.getOrderNo());
            row.put("customerName", customerNameMap.getOrDefault(order.getCustomerId(), ""));
            Product product = productMap.get(detail.getProductId());
            row.put("productName", product != null ? product.getName() : "");
            row.put("quantity", detail.getQuantity());
            row.put("salePrice", detail.getSalePrice());
            BigDecimal subtotal = BigDecimal.ZERO;
            if (detail.getQuantity() != null && detail.getSalePrice() != null) {
                subtotal = detail.getSalePrice().multiply(new BigDecimal(detail.getQuantity()));
            }
            row.put("subtotal", subtotal);
            resultList.add(row);

            totalQuantity += detail.getQuantity() != null ? detail.getQuantity() : 0;
            totalAmount = totalAmount.add(subtotal);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultList);
        Map<String, Object> total = new HashMap<>();
        total.put("totalQuantity", totalQuantity);
        total.put("totalAmount", totalAmount);
        result.put("total", total);
        return Result.success(result);
    }
}

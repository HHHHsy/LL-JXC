package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.Result;
import com.jxc.entity.Product;
import com.jxc.entity.PurchaseOrder;
import com.jxc.entity.PurchaseOrderDetail;
import com.jxc.entity.Supplier;
import com.jxc.service.PurchaseOrderDetailService;
import com.jxc.service.PurchaseOrderService;
import com.jxc.service.ProductService;
import com.jxc.service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase-order")
public class PurchaseOrderController {

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private PurchaseOrderDetailService purchaseOrderDetailService;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private ProductService productService;

    @PostMapping
    public Result createOrder(@RequestBody Map<String, Object> body) {
        try {
            PurchaseOrder order = parseOrder(body);
            List<PurchaseOrderDetail> details = parseDetails(body);
            return purchaseOrderService.createOrder(order, details);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    public Result updateOrder(@RequestBody Map<String, Object> body) {
        try {
            PurchaseOrder order = parseOrder(body);
            List<PurchaseOrderDetail> details = parseDetails(body);
            return purchaseOrderService.updateOrder(order, details);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        PurchaseOrder order = purchaseOrderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        QueryWrapper<PurchaseOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", id);
        List<PurchaseOrderDetail> details = purchaseOrderDetailService.list(wrapper);
        List<Map<String, Object>> detailList = new ArrayList<>();
        for (PurchaseOrderDetail detail : details) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", detail.getId());
            map.put("orderId", detail.getOrderId());
            map.put("productId", detail.getProductId());
            map.put("quantity", detail.getQuantity());
            map.put("receivedQuantity", detail.getReceivedQuantity());
            map.put("costPrice", detail.getCostPrice());
            Product product = productService.getById(detail.getProductId());
            map.put("productName", product != null ? product.getName() : "");
            detailList.add(map);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("details", detailList);
        return Result.success(result);
    }

    @DeleteMapping("/{id}")
    public Result deleteById(@PathVariable Long id) {
        PurchaseOrder order = purchaseOrderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        if (!"待入库".equals(order.getStatus())) {
            return Result.error("仅待入库状态的订单可删除");
        }
        QueryWrapper<PurchaseOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", id);
        purchaseOrderDetailService.remove(wrapper);
        purchaseOrderService.removeById(id);
        return Result.success();
    }

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String orderNo,
                       @RequestParam(required = false) Long supplierId,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate) {
        QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<>();
        if (orderNo != null && !orderNo.isEmpty()) {
            wrapper.like("order_no", orderNo);
        }
        if (supplierId != null) {
            wrapper.eq("supplier_id", supplierId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge("purchase_date", startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le("purchase_date", endDate);
        }
        wrapper.orderByDesc("create_time");
        List<PurchaseOrder> orders = purchaseOrderService.list(wrapper);
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (PurchaseOrder order : orders) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", order.getId());
            map.put("orderNo", order.getOrderNo());
            map.put("supplierId", order.getSupplierId());
            map.put("purchaseDate", order.getPurchaseDate());
            map.put("status", order.getStatus());
            map.put("createTime", order.getCreateTime());
            map.put("updateTime", order.getUpdateTime());
            if (order.getSupplierId() != null) {
                Supplier supplier = supplierService.getById(order.getSupplierId());
                map.put("supplierName", supplier != null ? supplier.getName() : "");
            } else {
                map.put("supplierName", "");
            }
            resultList.add(map);
        }
        return Result.success(resultList);
    }

    @PostMapping("/{id}/receive")
    public Result receiveStock(@PathVariable Long id, @RequestBody List<Map<String, Object>> detailList) {
        try {
            List<PurchaseOrderDetail> details = new ArrayList<>();
            for (Map<String, Object> map : detailList) {
                PurchaseOrderDetail detail = new PurchaseOrderDetail();
                detail.setProductId(Long.valueOf(map.get("productId").toString()));
                detail.setReceivedQuantity(Integer.valueOf(map.get("quantity").toString()));
                details.add(detail);
            }
            return purchaseOrderService.receiveStock(id, details);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private PurchaseOrder parseOrder(Map<String, Object> body) {
        Map<String, Object> orderMap = (Map<String, Object>) body.get("order");
        PurchaseOrder order = new PurchaseOrder();
        if (orderMap.get("id") != null) {
            order.setId(Long.valueOf(orderMap.get("id").toString()));
        }
        order.setSupplierId(Long.valueOf(orderMap.get("supplierId").toString()));
        if (orderMap.get("status") != null) {
            order.setStatus(orderMap.get("status").toString());
        }
        if (orderMap.get("purchaseDate") != null) {
            order.setPurchaseDate(LocalDate.parse(orderMap.get("purchaseDate").toString()));
        }
        return order;
    }

    @SuppressWarnings("unchecked")
    private List<PurchaseOrderDetail> parseDetails(Map<String, Object> body) {
        List<Map<String, Object>> detailMaps = (List<Map<String, Object>>) body.get("details");
        List<PurchaseOrderDetail> details = new ArrayList<>();
        for (Map<String, Object> map : detailMaps) {
            PurchaseOrderDetail detail = new PurchaseOrderDetail();
            if (map.get("id") != null) {
                detail.setId(Long.valueOf(map.get("id").toString()));
            }
            detail.setProductId(Long.valueOf(map.get("productId").toString()));
            detail.setQuantity(Integer.valueOf(map.get("quantity").toString()));
            if (map.get("costPrice") != null) {
                detail.setCostPrice(new java.math.BigDecimal(map.get("costPrice").toString()));
            }
            if (map.get("receivedQuantity") != null) {
                detail.setReceivedQuantity(Integer.valueOf(map.get("receivedQuantity").toString()));
            }
            details.add(detail);
        }
        return details;
    }
}

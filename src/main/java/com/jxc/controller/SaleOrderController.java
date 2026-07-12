package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.Result;
import com.jxc.entity.Customer;
import com.jxc.entity.Product;
import com.jxc.entity.SaleOrder;
import com.jxc.entity.SaleOrderDetail;
import com.jxc.service.CustomerService;
import com.jxc.service.ProductService;
import com.jxc.service.SaleOrderDetailService;
import com.jxc.service.SaleOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sale-order")
public class SaleOrderController {

    @Autowired
    private SaleOrderService saleOrderService;

    @Autowired
    private SaleOrderDetailService saleOrderDetailService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @PostMapping
    public Result createOrder(@RequestBody Map<String, Object> body) {
        try {
            SaleOrder order = parseOrder(body);
            List<SaleOrderDetail> details = parseDetails(body);
            return saleOrderService.createOrder(order, details);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    public Result updateOrder(@RequestBody Map<String, Object> body) {
        try {
            SaleOrder order = parseOrder(body);
            List<SaleOrderDetail> details = parseDetails(body);
            return saleOrderService.updateOrder(order, details);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        SaleOrder order = saleOrderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        QueryWrapper<SaleOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", id);
        List<SaleOrderDetail> details = saleOrderDetailService.list(wrapper);
        List<Map<String, Object>> detailList = new ArrayList<>();
        for (SaleOrderDetail detail : details) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", detail.getId());
            map.put("orderId", detail.getOrderId());
            map.put("productId", detail.getProductId());
            map.put("quantity", detail.getQuantity());
            map.put("shippedQuantity", detail.getShippedQuantity());
            map.put("salePrice", detail.getSalePrice());
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
        SaleOrder order = saleOrderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        if (!"待出库".equals(order.getStatus())) {
            return Result.error("仅待出库状态的订单可删除");
        }
        QueryWrapper<SaleOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", id);
        saleOrderDetailService.remove(wrapper);
        saleOrderService.removeById(id);
        return Result.success();
    }

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String orderNo,
                       @RequestParam(required = false) Long customerId,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate) {
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        if (orderNo != null && !orderNo.isEmpty()) {
            wrapper.like("order_no", orderNo);
        }
        if (customerId != null) {
            wrapper.eq("customer_id", customerId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge("sale_date", startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le("sale_date", endDate);
        }
        wrapper.orderByDesc("create_time");
        List<SaleOrder> orders = saleOrderService.list(wrapper);
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (SaleOrder order : orders) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", order.getId());
            map.put("orderNo", order.getOrderNo());
            map.put("customerId", order.getCustomerId());
            map.put("saleDate", order.getSaleDate());
            map.put("status", order.getStatus());
            map.put("createTime", order.getCreateTime());
            map.put("updateTime", order.getUpdateTime());
            if (order.getCustomerId() != null) {
                Customer customer = customerService.getById(order.getCustomerId());
                map.put("customerName", customer != null ? customer.getName() : "");
            } else {
                map.put("customerName", "");
            }
            resultList.add(map);
        }
        return Result.success(resultList);
    }

    @PostMapping("/{id}/ship")
    public Result shipStock(@PathVariable Long id, @RequestBody List<Map<String, Object>> detailList) {
        try {
            List<SaleOrderDetail> details = new ArrayList<>();
            for (Map<String, Object> map : detailList) {
                SaleOrderDetail detail = new SaleOrderDetail();
                detail.setProductId(Long.valueOf(map.get("productId").toString()));
                detail.setShippedQuantity(Integer.valueOf(map.get("quantity").toString()));
                details.add(detail);
            }
            return saleOrderService.shipStock(id, details);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private SaleOrder parseOrder(Map<String, Object> body) {
        Map<String, Object> orderMap = (Map<String, Object>) body.get("order");
        SaleOrder order = new SaleOrder();
        if (orderMap.get("id") != null) {
            order.setId(Long.valueOf(orderMap.get("id").toString()));
        }
        order.setCustomerId(Long.valueOf(orderMap.get("customerId").toString()));
        if (orderMap.get("status") != null) {
            order.setStatus(orderMap.get("status").toString());
        }
        if (orderMap.get("saleDate") != null) {
            order.setSaleDate(LocalDate.parse(orderMap.get("saleDate").toString()));
        }
        return order;
    }

    @SuppressWarnings("unchecked")
    private List<SaleOrderDetail> parseDetails(Map<String, Object> body) {
        List<Map<String, Object>> detailMaps = (List<Map<String, Object>>) body.get("details");
        List<SaleOrderDetail> details = new ArrayList<>();
        for (Map<String, Object> map : detailMaps) {
            SaleOrderDetail detail = new SaleOrderDetail();
            if (map.get("id") != null) {
                detail.setId(Long.valueOf(map.get("id").toString()));
            }
            detail.setProductId(Long.valueOf(map.get("productId").toString()));
            detail.setQuantity(Integer.valueOf(map.get("quantity").toString()));
            if (map.get("salePrice") != null) {
                detail.setSalePrice(new java.math.BigDecimal(map.get("salePrice").toString()));
            }
            if (map.get("shippedQuantity") != null) {
                detail.setShippedQuantity(Integer.valueOf(map.get("shippedQuantity").toString()));
            }
            details.add(detail);
        }
        return details;
    }
}

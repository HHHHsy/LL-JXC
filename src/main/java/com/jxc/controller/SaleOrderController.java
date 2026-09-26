package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jxc.common.OrderStatus;
import com.jxc.common.PageResult;
import com.jxc.common.Result;
import com.jxc.dto.SaleOrderDetailForm;
import com.jxc.dto.SaleOrderForm;
import com.jxc.dto.SaleOrderSaveRequest;
import com.jxc.dto.StockMoveItem;
import com.jxc.entity.Customer;
import com.jxc.entity.Product;
import com.jxc.entity.SaleOrder;
import com.jxc.entity.SaleOrderDetail;
import com.jxc.service.CustomerService;
import com.jxc.service.DeliveryNoteService;
import com.jxc.service.ProductService;
import com.jxc.service.SaleOrderDetailService;
import com.jxc.service.SaleOrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Autowired
    private DeliveryNoteService deliveryNoteService;

    /**
     * 送货单打印数据（抬头、客户、明细、合计与金额大写）。
     */
    @GetMapping("/{id}/delivery-note")
    public Result deliveryNote(@PathVariable Long id) {
        return Result.success(deliveryNoteService.buildForSaleOrder(id));
    }

    /** 新增销售订单（请求体走 DTO + 校验） */
    @PostMapping
    public Result createOrder(@Valid @RequestBody SaleOrderSaveRequest request) {
        return saleOrderService.createOrder(toOrder(request.getOrder()), toDetails(request.getDetails()));
    }

    /** 修改销售订单（仅待出库可改） */
    @PutMapping
    public Result updateOrder(@Valid @RequestBody SaleOrderSaveRequest request) {
        return saleOrderService.updateOrder(toOrder(request.getOrder()), toDetails(request.getDetails()));
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        SaleOrder order = saleOrderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        QueryWrapper<SaleOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", id);
        wrapper.orderByAsc("id");
        List<SaleOrderDetail> details = saleOrderDetailService.list(wrapper);

        Set<Long> productIds = details.stream().map(SaleOrderDetail::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> productNames = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Product p : productService.listByIds(productIds)) {
                productNames.put(p.getId(), p.getName());
            }
        }

        List<Map<String, Object>> detailList = new ArrayList<>();
        for (SaleOrderDetail detail : details) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", detail.getId());
            map.put("orderId", detail.getOrderId());
            map.put("productId", detail.getProductId());
            map.put("quantity", detail.getQuantity());
            map.put("shippedQuantity", detail.getShippedQuantity());
            map.put("salePrice", detail.getSalePrice());
            map.put("remark", detail.getRemark());
            map.put("productName", productNames.getOrDefault(detail.getProductId(), ""));
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
        if (!OrderStatus.SALE_PENDING.equals(order.getStatus()) && !OrderStatus.CANCELED.equals(order.getStatus())) {
            return Result.error("仅待出库/已取消状态的订单可删除");
        }
        QueryWrapper<SaleOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", id);
        saleOrderDetailService.remove(wrapper);
        saleOrderService.removeById(id);
        return Result.success();
    }

    /** 取消订单（仅待出库状态） */
    @PostMapping("/{id}/cancel")
    public Result cancel(@PathVariable Long id) {
        return saleOrderService.cancelOrder(id);
    }

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String orderNo,
                       @RequestParam(required = false) Long customerId,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate,
                       @RequestParam(defaultValue = "1") long pageNum,
                       @RequestParam(defaultValue = "10") long pageSize) {
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        if (orderNo != null && !orderNo.isEmpty()) {
            wrapper.like("order_no", orderNo);
        }
        if (customerId != null) {
            wrapper.eq("customer_id", customerId);
        }
        applyStatusFilter(wrapper, status);
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge("sale_date", startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le("sale_date", endDate);
        }
        wrapper.orderByDesc("id");

        Page<SaleOrder> page = saleOrderService.page(new Page<>(pageNum, pageSize), wrapper);
        List<SaleOrder> orders = page.getRecords();

        Set<Long> customerIds = orders.stream().map(SaleOrder::getCustomerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> customerNames = new HashMap<>();
        if (!customerIds.isEmpty()) {
            for (Customer c : customerService.listByIds(customerIds)) {
                customerNames.put(c.getId(), c.getName());
            }
        }

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
            map.put("customerName", customerNames.getOrDefault(order.getCustomerId(), ""));
            resultList.add(map);
        }
        return Result.success(PageResult.of(resultList, page.getTotal(), pageNum, pageSize));
    }

    /** 销售出库：支持分批，body 为 [{id, productId, quantity}]，quantity 为本次出库量 */
    @PostMapping("/{id}/ship")
    public Result shipStock(@PathVariable Long id, @Valid @RequestBody List<StockMoveItem> items) {
        return saleOrderService.shipStock(id, toShipDetails(items));
    }

    /** status 支持逗号分隔的多状态，如 "待出库,部分出库" */
    private void applyStatusFilter(QueryWrapper<SaleOrder> wrapper, String status) {
        if (status == null || status.isEmpty()) {
            return;
        }
        if (status.contains(",")) {
            wrapper.in("status", Arrays.asList(status.split(",")));
        } else {
            wrapper.eq("status", status);
        }
    }

    private SaleOrder toOrder(SaleOrderForm form) {
        SaleOrder order = new SaleOrder();
        order.setId(form.getId());
        order.setCustomerId(form.getCustomerId());
        order.setSaleDate(form.getSaleDate());
        return order;
    }

    private List<SaleOrderDetail> toDetails(List<SaleOrderDetailForm> forms) {
        List<SaleOrderDetail> details = new ArrayList<>();
        if (forms == null) {
            return details;
        }
        for (SaleOrderDetailForm form : forms) {
            SaleOrderDetail detail = new SaleOrderDetail();
            detail.setId(form.getId());
            detail.setProductId(form.getProductId());
            detail.setQuantity(form.getQuantity());
            detail.setSalePrice(form.getSalePrice() == null ? BigDecimal.ZERO : form.getSalePrice());
            detail.setRemark(form.getRemark() == null ? "" : form.getRemark().trim());
            details.add(detail);
        }
        return details;
    }

    private List<SaleOrderDetail> toShipDetails(List<StockMoveItem> items) {
        List<SaleOrderDetail> details = new ArrayList<>();
        if (items == null) {
            return details;
        }
        for (StockMoveItem item : items) {
            SaleOrderDetail detail = new SaleOrderDetail();
            detail.setId(item.getId());
            detail.setProductId(item.getProductId());
            detail.setShippedQuantity(item.getQuantity());
            details.add(detail);
        }
        return details;
    }
}

package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jxc.common.ExcelTemplate;
import com.jxc.common.FileResponses;
import com.jxc.common.OrderStatus;
import com.jxc.common.PageResult;
import com.jxc.common.Result;
import com.jxc.dto.PurchaseOrderDetailForm;
import com.jxc.dto.PurchaseOrderForm;
import com.jxc.dto.PurchaseOrderSaveRequest;
import com.jxc.dto.StockMoveItem;
import com.jxc.entity.Product;
import com.jxc.entity.PurchaseOrder;
import com.jxc.entity.PurchaseOrderDetail;
import com.jxc.entity.Supplier;
import com.jxc.service.ProductService;
import com.jxc.service.PurchaseImportService;
import com.jxc.service.PurchaseOrderDetailService;
import com.jxc.service.PurchaseOrderService;
import com.jxc.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Autowired
    private PurchaseImportService purchaseImportService;

    /** 新增采购订单（请求体走 DTO + 校验） */
    @PostMapping
    public Result createOrder(@Valid @RequestBody PurchaseOrderSaveRequest request) {
        return purchaseOrderService.createOrder(toOrder(request.getOrder()), toDetails(request.getDetails()));
    }

    /** 修改采购订单（仅待入库可改） */
    @PutMapping
    public Result updateOrder(@Valid @RequestBody PurchaseOrderSaveRequest request) {
        return purchaseOrderService.updateOrder(toOrder(request.getOrder()), toDetails(request.getDetails()));
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        PurchaseOrder order = purchaseOrderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        QueryWrapper<PurchaseOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", id);
        wrapper.orderByAsc("id");
        List<PurchaseOrderDetail> details = purchaseOrderDetailService.list(wrapper);

        Set<Long> productIds = details.stream().map(PurchaseOrderDetail::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> productNames = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Product p : productService.listByIds(productIds)) {
                productNames.put(p.getId(), p.getName());
            }
        }

        List<Map<String, Object>> detailList = new ArrayList<>();
        for (PurchaseOrderDetail detail : details) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", detail.getId());
            map.put("orderId", detail.getOrderId());
            map.put("productId", detail.getProductId());
            map.put("quantity", detail.getQuantity());
            map.put("receivedQuantity", detail.getReceivedQuantity());
            map.put("costPrice", detail.getCostPrice());
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
        PurchaseOrder order = purchaseOrderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        if (!OrderStatus.PURCHASE_PENDING.equals(order.getStatus()) && !OrderStatus.CANCELED.equals(order.getStatus())) {
            return Result.error("仅待入库/已取消状态的订单可删除");
        }
        QueryWrapper<PurchaseOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", id);
        purchaseOrderDetailService.remove(wrapper);
        purchaseOrderService.removeById(id);
        return Result.success();
    }

    /** 取消订单（仅待入库状态） */
    @PostMapping("/{id}/cancel")
    public Result cancel(@PathVariable Long id) {
        return purchaseOrderService.cancelOrder(id);
    }

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String orderNo,
                       @RequestParam(required = false) Long supplierId,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate,
                       @RequestParam(defaultValue = "1") long pageNum,
                       @RequestParam(defaultValue = "10") long pageSize) {
        QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<>();
        if (orderNo != null && !orderNo.isEmpty()) {
            wrapper.like("order_no", orderNo);
        }
        if (supplierId != null) {
            wrapper.eq("supplier_id", supplierId);
        }
        applyStatusFilter(wrapper, status);
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge("purchase_date", startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le("purchase_date", endDate);
        }
        wrapper.orderByDesc("id");

        Page<PurchaseOrder> page = purchaseOrderService.page(new Page<>(pageNum, pageSize), wrapper);
        List<PurchaseOrder> orders = page.getRecords();

        Set<Long> supplierIds = orders.stream().map(PurchaseOrder::getSupplierId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> supplierNames = new HashMap<>();
        if (!supplierIds.isEmpty()) {
            for (Supplier s : supplierService.listByIds(supplierIds)) {
                supplierNames.put(s.getId(), s.getName());
            }
        }

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
            map.put("supplierName", supplierNames.getOrDefault(order.getSupplierId(), ""));
            resultList.add(map);
        }
        return Result.success(PageResult.of(resultList, page.getTotal(), pageNum, pageSize));
    }

    /** 采购入库：支持分批，body 为 [{id, productId, quantity}]，quantity 为本次入库量 */
    @PostMapping("/{id}/receive")
    public Result receiveStock(@PathVariable Long id, @Valid @RequestBody List<StockMoveItem> items) {
        return purchaseOrderService.receiveStock(id, toReceiveDetails(items));
    }

    /**
     * 采购清单 Excel 导入：下载示例模板。
     */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() {
        return FileResponses.download(ExcelTemplate.purchaseTemplate(), "采购清单导入示例.xlsx");
    }

    /**
     * 采购清单 Excel 导入：仅解析并校验，用于导入前预览。
     */
    @PostMapping("/import/preview")
    public Result previewImport(@RequestParam("file") MultipartFile file,
                                @RequestParam(defaultValue = "true") boolean autoCreateProduct) {
        return Result.success(purchaseImportService.parse(file, autoCreateProduct));
    }

    /**
     * 采购清单 Excel 导入：解析并生成一张采购订单。
     */
    @PostMapping("/import")
    public Result importFromExcel(@RequestParam("file") MultipartFile file,
                                  @RequestParam Long supplierId,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDate,
                                  @RequestParam(defaultValue = "true") boolean autoCreateProduct,
                                  @RequestParam(defaultValue = "false") boolean skipErrorRows) {
        return Result.success(purchaseImportService.importOrder(
                file, supplierId, purchaseDate, autoCreateProduct, skipErrorRows));
    }

    /** status 支持逗号分隔的多状态，如 "待入库,部分入库" */
    private void applyStatusFilter(QueryWrapper<PurchaseOrder> wrapper, String status) {
        if (status == null || status.isEmpty()) {
            return;
        }
        if (status.contains(",")) {
            wrapper.in("status", Arrays.asList(status.split(",")));
        } else {
            wrapper.eq("status", status);
        }
    }

    private PurchaseOrder toOrder(PurchaseOrderForm form) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(form.getId());
        order.setSupplierId(form.getSupplierId());
        order.setPurchaseDate(form.getPurchaseDate());
        return order;
    }

    private List<PurchaseOrderDetail> toDetails(List<PurchaseOrderDetailForm> forms) {
        List<PurchaseOrderDetail> details = new ArrayList<>();
        if (forms == null) {
            return details;
        }
        for (PurchaseOrderDetailForm form : forms) {
            PurchaseOrderDetail detail = new PurchaseOrderDetail();
            detail.setId(form.getId());
            detail.setProductId(form.getProductId());
            detail.setQuantity(form.getQuantity());
            detail.setCostPrice(form.getCostPrice() == null ? BigDecimal.ZERO : form.getCostPrice());
            detail.setRemark(form.getRemark() == null ? "" : form.getRemark().trim());
            details.add(detail);
        }
        return details;
    }

    private List<PurchaseOrderDetail> toReceiveDetails(List<StockMoveItem> items) {
        List<PurchaseOrderDetail> details = new ArrayList<>();
        if (items == null) {
            return details;
        }
        for (StockMoveItem item : items) {
            PurchaseOrderDetail detail = new PurchaseOrderDetail();
            detail.setId(item.getId());
            detail.setProductId(item.getProductId());
            detail.setReceivedQuantity(item.getQuantity());
            details.add(detail);
        }
        return details;
    }
}

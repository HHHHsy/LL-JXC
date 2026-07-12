package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.Result;
import com.jxc.entity.InventoryLog;
import com.jxc.entity.Product;
import com.jxc.service.InventoryLogService;
import com.jxc.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory-log")
public class InventoryLogController {

    @Autowired
    private InventoryLogService inventoryLogService;

    @Autowired
    private ProductService productService;

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) Long productId,
                       @RequestParam(required = false) String changeType,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate) {
        QueryWrapper<InventoryLog> wrapper = new QueryWrapper<>();
        if (productId != null) {
            wrapper.eq("product_id", productId);
        }
        if (changeType != null && !changeType.isEmpty()) {
            wrapper.eq("change_type", changeType);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge("change_time", startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le("change_time", endDate);
        }
        wrapper.orderByDesc("change_time");
        List<InventoryLog> logs = inventoryLogService.list(wrapper);
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (InventoryLog log : logs) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", log.getId());
            map.put("changeTime", log.getChangeTime());
            map.put("productId", log.getProductId());
            map.put("changeType", log.getChangeType());
            map.put("changeQuantity", log.getChangeQuantity());
            map.put("afterStock", log.getAfterStock());
            map.put("relatedOrderNo", log.getRelatedOrderNo());
            map.put("remark", log.getRemark());
            map.put("createTime", log.getCreateTime());
            if (log.getProductId() != null) {
                Product product = productService.getById(log.getProductId());
                map.put("productName", product != null ? product.getName() : "");
            } else {
                map.put("productName", "");
            }
            resultList.add(map);
        }
        return Result.success(resultList);
    }
}

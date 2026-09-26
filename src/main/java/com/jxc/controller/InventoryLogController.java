package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jxc.common.PageResult;
import com.jxc.common.Result;
import com.jxc.entity.InventoryLog;
import com.jxc.entity.Product;
import com.jxc.service.InventoryLogService;
import com.jxc.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
                       @RequestParam(required = false) String endDate,
                       @RequestParam(defaultValue = "1") long pageNum,
                       @RequestParam(defaultValue = "10") long pageSize) {
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
            // change_time 是 datetime：直接 <= 'yyyy-MM-dd' 会丢掉结束当天的数据，因此用「小于次日」
            try {
                wrapper.lt("change_time", LocalDate.parse(endDate).plusDays(1).toString());
            } catch (DateTimeParseException e) {
                wrapper.le("change_time", endDate + " 23:59:59");
            }
        }
        wrapper.orderByDesc("change_time").orderByDesc("id");

        Page<InventoryLog> page = inventoryLogService.page(new Page<>(pageNum, pageSize), wrapper);
        List<InventoryLog> logs = page.getRecords();

        Set<Long> productIds = logs.stream().map(InventoryLog::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> productNames = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Product p : productService.listByIds(productIds)) {
                productNames.put(p.getId(), p.getName());
            }
        }

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
            map.put("productName", productNames.getOrDefault(log.getProductId(), ""));
            resultList.add(map);
        }
        return Result.success(PageResult.of(resultList, page.getTotal(), pageNum, pageSize));
    }
}

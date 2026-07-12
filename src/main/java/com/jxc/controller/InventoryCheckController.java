package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.Result;
import com.jxc.entity.InventoryCheck;
import com.jxc.entity.InventoryCheckDetail;
import com.jxc.entity.Product;
import com.jxc.service.InventoryCheckDetailService;
import com.jxc.service.InventoryCheckService;
import com.jxc.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory-check")
public class InventoryCheckController {

    @Autowired
    private InventoryCheckService inventoryCheckService;

    @Autowired
    private InventoryCheckDetailService inventoryCheckDetailService;

    @Autowired
    private ProductService productService;

    @PostMapping
    public Result createCheck(@RequestBody Map<String, Object> body) {
        try {
            InventoryCheck check = parseCheck(body);
            List<InventoryCheckDetail> details = parseDetails(body);
            return inventoryCheckService.createCheck(check, details);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        InventoryCheck check = inventoryCheckService.getById(id);
        if (check == null) {
            return Result.error("盘点单不存在");
        }
        QueryWrapper<InventoryCheckDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("check_id", id);
        List<InventoryCheckDetail> details = inventoryCheckDetailService.list(wrapper);

        List<Map<String, Object>> detailList = new ArrayList<>();
        for (InventoryCheckDetail detail : details) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", detail.getId());
            map.put("checkId", detail.getCheckId());
            map.put("productId", detail.getProductId());
            map.put("systemQuantity", detail.getSystemQuantity());
            map.put("actualQuantity", detail.getActualQuantity());
            map.put("diffQuantity", detail.getDiffQuantity());
            map.put("createTime", detail.getCreateTime());
            if (detail.getProductId() != null) {
                Product product = productService.getById(detail.getProductId());
                if (product != null) {
                    map.put("productName", product.getName());
                    map.put("productCode", product.getCode());
                }
            }
            detailList.add(map);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("check", check);
        result.put("details", detailList);
        return Result.success(result);
    }

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String checkNo,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate) {
        QueryWrapper<InventoryCheck> wrapper = new QueryWrapper<>();
        if (checkNo != null && !checkNo.isEmpty()) {
            wrapper.like("check_no", checkNo);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge("check_date", startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le("check_date", endDate);
        }
        wrapper.orderByDesc("create_time");
        return Result.success(inventoryCheckService.list(wrapper));
    }

    @SuppressWarnings("unchecked")
    private InventoryCheck parseCheck(Map<String, Object> body) {
        Map<String, Object> checkMap = (Map<String, Object>) body.get("check");
        InventoryCheck check = new InventoryCheck();
        if (checkMap.get("remark") != null) {
            check.setRemark(checkMap.get("remark").toString());
        }
        return check;
    }

    @SuppressWarnings("unchecked")
    private List<InventoryCheckDetail> parseDetails(Map<String, Object> body) {
        List<Map<String, Object>> detailMaps = (List<Map<String, Object>>) body.get("details");
        List<InventoryCheckDetail> details = new ArrayList<>();
        for (Map<String, Object> map : detailMaps) {
            InventoryCheckDetail detail = new InventoryCheckDetail();
            detail.setProductId(Long.valueOf(map.get("productId").toString()));
            detail.setSystemQuantity(Integer.valueOf(map.get("systemQuantity").toString()));
            detail.setActualQuantity(Integer.valueOf(map.get("actualQuantity").toString()));
            details.add(detail);
        }
        return details;
    }
}

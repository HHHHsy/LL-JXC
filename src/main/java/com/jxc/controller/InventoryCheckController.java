package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jxc.common.PageResult;
import com.jxc.common.Result;
import com.jxc.entity.InventoryCheck;
import com.jxc.entity.InventoryCheckDetail;
import com.jxc.entity.Product;
import com.jxc.service.InventoryCheckDetailService;
import com.jxc.service.InventoryCheckService;
import com.jxc.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
        return inventoryCheckService.createCheck(parseCheck(body), parseDetails(body));
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        InventoryCheck check = inventoryCheckService.getById(id);
        if (check == null) {
            return Result.error("盘点单不存在");
        }
        QueryWrapper<InventoryCheckDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("check_id", id);
        wrapper.orderByAsc("id");
        List<InventoryCheckDetail> details = inventoryCheckDetailService.list(wrapper);

        Set<Long> productIds = details.stream().map(InventoryCheckDetail::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Product> productMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Product p : productService.listByIds(productIds)) {
                productMap.put(p.getId(), p);
            }
        }

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
            Product product = productMap.get(detail.getProductId());
            if (product != null) {
                map.put("productName", product.getName());
                map.put("productCode", product.getCode());
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
                       @RequestParam(required = false) String endDate,
                       @RequestParam(defaultValue = "1") long pageNum,
                       @RequestParam(defaultValue = "10") long pageSize) {
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
        wrapper.orderByDesc("id");

        Page<InventoryCheck> page = inventoryCheckService.page(new Page<>(pageNum, pageSize), wrapper);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @SuppressWarnings("unchecked")
    private InventoryCheck parseCheck(Map<String, Object> body) {
        Map<String, Object> checkMap = (Map<String, Object>) body.get("check");
        InventoryCheck check = new InventoryCheck();
        if (checkMap == null) {
            return check;
        }
        // 盘点日期：前端必填；此处必须显式解析，否则服务层会回落到当天
        Object checkDate = checkMap.get("checkDate");
        if (checkDate != null && !checkDate.toString().isEmpty()) {
            check.setCheckDate(LocalDate.parse(checkDate.toString()));
        }
        if (checkMap.get("remark") != null) {
            check.setRemark(checkMap.get("remark").toString());
        }
        return check;
    }

    @SuppressWarnings("unchecked")
    private List<InventoryCheckDetail> parseDetails(Map<String, Object> body) {
        List<Map<String, Object>> detailMaps = (List<Map<String, Object>>) body.get("details");
        if (detailMaps == null || detailMaps.isEmpty()) {
            throw new RuntimeException("请至少录入一条盘点明细");
        }
        List<InventoryCheckDetail> details = new ArrayList<>();
        for (int i = 0; i < detailMaps.size(); i++) {
            Map<String, Object> map = detailMaps.get(i);
            int rowNo = i + 1;
            if (map.get("productId") == null) {
                throw new RuntimeException("第 " + rowNo + " 行缺少商品");
            }
            if (map.get("actualQuantity") == null) {
                throw new RuntimeException("第 " + rowNo + " 行请填写实盘数量");
            }
            InventoryCheckDetail detail = new InventoryCheckDetail();
            detail.setProductId(Long.valueOf(map.get("productId").toString()));
            // systemQuantity 由服务端按数据库当前库存重新计算，前端传值仅作参考
            detail.setActualQuantity(Integer.valueOf(map.get("actualQuantity").toString()));
            details.add(detail);
        }
        return details;
    }
}

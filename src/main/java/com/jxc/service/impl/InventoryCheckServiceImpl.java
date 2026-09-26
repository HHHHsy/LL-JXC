package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxc.common.OrderNoUtils;
import com.jxc.common.Result;
import com.jxc.entity.InventoryCheck;
import com.jxc.entity.InventoryCheckDetail;
import com.jxc.entity.InventoryLog;
import com.jxc.entity.Product;
import com.jxc.mapper.InventoryCheckMapper;
import com.jxc.service.InventoryCheckDetailService;
import com.jxc.service.InventoryCheckService;
import com.jxc.service.InventoryLogService;
import com.jxc.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class InventoryCheckServiceImpl extends ServiceImpl<InventoryCheckMapper, InventoryCheck> implements InventoryCheckService {

    @Autowired
    private InventoryCheckDetailService inventoryCheckDetailService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryLogService inventoryLogService;

    @Override
    @Transactional
    public Result createCheck(InventoryCheck check, List<InventoryCheckDetail> details) {
        if (details == null || details.isEmpty()) {
            throw new RuntimeException("请至少录入一条盘点明细");
        }
        if (check.getCheckDate() == null) {
            check.setCheckDate(LocalDate.now());
        }
        check.setCheckNo(generateCheckNo());
        save(check);

        // 1) 系统库存以数据库当前值为准（不信任前端传来的快照），并计算盈亏
        java.util.Set<Long> seenProducts = new java.util.HashSet<>();
        for (InventoryCheckDetail detail : details) {
            if (detail.getProductId() == null) {
                throw new RuntimeException("盘点明细缺少商品");
            }
            if (detail.getActualQuantity() == null) {
                throw new RuntimeException("请填写实盘数量");
            }
            if (detail.getActualQuantity() < 0) {
                throw new RuntimeException("实盘数量不能为负数");
            }
            // 同一商品出现多行会让盈亏互相叠加、系统库存快照失真，直接拦下
            if (!seenProducts.add(detail.getProductId())) {
                throw new RuntimeException("同一商品在盘点明细中重复，请合并为一行后再提交");
            }
            Product product = productService.getById(detail.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在（id=" + detail.getProductId() + "）");
            }
            int systemQty = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            detail.setId(null);
            detail.setCheckId(check.getId());
            detail.setSystemQuantity(systemQty);
            detail.setDiffQuantity(detail.getActualQuantity() - systemQty);
        }
        inventoryCheckDetailService.saveBatch(details);

        // 2) 按盈亏调整库存并写流水
        for (InventoryCheckDetail detail : details) {
            if (detail.getDiffQuantity() == null || detail.getDiffQuantity() == 0) {
                continue;
            }
            Product product = productService.getById(detail.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在（id=" + detail.getProductId() + "）");
            }
            int stock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            int newStock = stock + detail.getDiffQuantity();
            if (newStock < 0) {
                throw new RuntimeException("商品【" + product.getName() + "】盘亏后库存为负数，请核对实盘数量");
            }
            product.setStockQuantity(newStock);
            if (!productService.updateById(product)) {
                throw new RuntimeException("商品【" + product.getName() + "】库存已被其他操作修改，请重新盘点");
            }

            boolean profit = detail.getDiffQuantity() > 0;
            InventoryLog log = new InventoryLog();
            log.setProductId(detail.getProductId());
            log.setChangeType(profit ? "入库" : "出库");
            log.setChangeQuantity(Math.abs(detail.getDiffQuantity()));
            log.setAfterStock(newStock);
            log.setRelatedOrderNo(check.getCheckNo());
            log.setRemark(profit ? "盘盈" : "盘亏");
            inventoryLogService.save(log);
        }

        return Result.success();
    }

    private String generateCheckNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<InventoryCheck> wrapper = new QueryWrapper<>();
        wrapper.likeRight("check_no", "PD" + dateStr);
        wrapper.orderByDesc("check_no");
        wrapper.last("limit 1");
        InventoryCheck last = baseMapper.selectOne(wrapper);
        return OrderNoUtils.next("PD", dateStr, last == null ? null : last.getCheckNo());
    }
}

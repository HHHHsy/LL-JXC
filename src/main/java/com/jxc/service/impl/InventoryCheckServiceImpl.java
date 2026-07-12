package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import java.time.LocalDateTime;
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
        String checkNo = generateCheckNo("PD");
        check.setCheckNo(checkNo);
        if (check.getCheckDate() == null) {
            check.setCheckDate(LocalDate.now());
        }
        save(check);

        for (InventoryCheckDetail detail : details) {
            detail.setCheckId(check.getId());
            detail.setDiffQuantity(detail.getActualQuantity() - detail.getSystemQuantity());
        }
        inventoryCheckDetailService.saveBatch(details);

        for (InventoryCheckDetail detail : details) {
            if (detail.getDiffQuantity() != 0) {
                Product product = productService.getById(detail.getProductId());
                if (product == null) {
                    throw new RuntimeException("商品不存在");
                }
                if (detail.getDiffQuantity() > 0) {
                    product.setStockQuantity(product.getStockQuantity() + detail.getDiffQuantity());
                    productService.updateById(product);

                    InventoryLog log = new InventoryLog();
                    log.setProductId(detail.getProductId());
                    log.setChangeType("入库");
                    log.setChangeQuantity(detail.getDiffQuantity());
                    log.setAfterStock(product.getStockQuantity());
                    log.setRelatedOrderNo(check.getCheckNo());
                    log.setRemark("盘盈");
                    inventoryLogService.save(log);
                } else {
                    product.setStockQuantity(product.getStockQuantity() + detail.getDiffQuantity());
                    productService.updateById(product);

                    InventoryLog log = new InventoryLog();
                    log.setProductId(detail.getProductId());
                    log.setChangeType("出库");
                    log.setChangeQuantity(Math.abs(detail.getDiffQuantity()));
                    log.setAfterStock(product.getStockQuantity());
                    log.setRelatedOrderNo(check.getCheckNo());
                    log.setRemark("盘亏");
                    inventoryLogService.save(log);
                }
            }
        }

        return Result.success();
    }

    private String generateCheckNo(String prefix) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<InventoryCheck> wrapper = new QueryWrapper<>();
        wrapper.likeRight("check_no", prefix + dateStr);
        wrapper.orderByDesc("check_no");
        wrapper.last("limit 1");
        InventoryCheck last = baseMapper.selectOne(wrapper);
        int seq = 1;
        if (last != null) {
            String lastNo = last.getCheckNo();
            seq = Integer.parseInt(lastNo.substring(lastNo.length() - 3)) + 1;
        }
        return prefix + dateStr + String.format("%03d", seq);
    }
}

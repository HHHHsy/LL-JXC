package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxc.common.Result;
import com.jxc.entity.InventoryLog;
import com.jxc.entity.Product;
import com.jxc.entity.SaleOrder;
import com.jxc.entity.SaleOrderDetail;
import com.jxc.mapper.SaleOrderMapper;
import com.jxc.service.InventoryLogService;
import com.jxc.service.ProductService;
import com.jxc.service.SaleOrderDetailService;
import com.jxc.service.SaleOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SaleOrderServiceImpl extends ServiceImpl<SaleOrderMapper, SaleOrder> implements SaleOrderService {

    @Autowired
    private SaleOrderDetailService saleOrderDetailService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryLogService inventoryLogService;

    @Override
    @Transactional
    public Result createOrder(SaleOrder order, List<SaleOrderDetail> details) {
        String orderNo = generateOrderNo("XS");
        order.setOrderNo(orderNo);
        order.setStatus("待出库");
        if (order.getSaleDate() == null) {
            order.setSaleDate(LocalDate.now());
        }
        save(order);

        for (SaleOrderDetail detail : details) {
            detail.setOrderId(order.getId());
            if (detail.getShippedQuantity() == null) {
                detail.setShippedQuantity(0);
            }
        }
        saleOrderDetailService.saveBatch(details);

        return Result.success();
    }

    @Override
    @Transactional
    public Result updateOrder(SaleOrder order, List<SaleOrderDetail> details) {
        SaleOrder dbOrder = getById(order.getId());
        if (!"待出库".equals(dbOrder.getStatus())) {
            throw new RuntimeException("仅待出库状态的订单可编辑");
        }
        updateById(order);

        QueryWrapper<SaleOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", order.getId());
        saleOrderDetailService.remove(wrapper);

        for (SaleOrderDetail detail : details) {
            detail.setOrderId(order.getId());
            if (detail.getShippedQuantity() == null) {
                detail.setShippedQuantity(0);
            }
        }
        saleOrderDetailService.saveBatch(details);

        return Result.success();
    }

    @Override
    @Transactional
    public Result shipStock(Long orderId, List<SaleOrderDetail> shipDetails) {
        SaleOrder order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("销售订单不存在");
        }
        String status = order.getStatus();
        if (!"待出库".equals(status) && !"部分出库".equals(status)) {
            throw new RuntimeException("当前订单状态不允许出库操作");
        }

        QueryWrapper<SaleOrderDetail> detailWrapper = new QueryWrapper<>();
        detailWrapper.eq("order_id", orderId);
        List<SaleOrderDetail> originDetails = saleOrderDetailService.list(detailWrapper);

        for (SaleOrderDetail shipDetail : shipDetails) {
            SaleOrderDetail originDetail = null;
            for (SaleOrderDetail od : originDetails) {
                if (od.getProductId().equals(shipDetail.getProductId())) {
                    originDetail = od;
                    break;
                }
            }
            if (originDetail == null) {
                throw new RuntimeException("未找到对应的原始明细");
            }

            int shippedQty = shipDetail.getShippedQuantity() == null ? 0 : shipDetail.getShippedQuantity();
            if (shippedQty <= 0) {
                throw new RuntimeException("本次出库数量必须大于0");
            }
            int maxShip = originDetail.getQuantity() - originDetail.getShippedQuantity();
            if (shippedQty > maxShip) {
                throw new RuntimeException("本次出库数量超出可出库数量");
            }

            Product product = productService.getById(originDetail.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在");
            }
            if (product.getStockQuantity() < shippedQty) {
                throw new RuntimeException("商品【" + product.getName() + "】库存不足，当前库存：" + product.getStockQuantity() + "，需要出库：" + shippedQty + "，缺货数量：" + (shippedQty - product.getStockQuantity()));
            }

            originDetail.setShippedQuantity(originDetail.getShippedQuantity() + shippedQty);
            saleOrderDetailService.updateById(originDetail);

            product.setStockQuantity(product.getStockQuantity() - shippedQty);
            productService.updateById(product);

            InventoryLog log = new InventoryLog();
            log.setProductId(originDetail.getProductId());
            log.setChangeType("出库");
            log.setChangeQuantity(shippedQty);
            log.setAfterStock(product.getStockQuantity());
            log.setRelatedOrderNo(order.getOrderNo());
            log.setRemark("");
            inventoryLogService.save(log);
        }

        // 刷新明细列表，检查是否全部出库
        List<SaleOrderDetail> updatedDetails = saleOrderDetailService.list(detailWrapper);
        boolean allShipped = true;
        for (SaleOrderDetail detail : updatedDetails) {
            if (detail.getShippedQuantity() < detail.getQuantity()) {
                allShipped = false;
                break;
            }
        }
        if (allShipped) {
            order.setStatus("已出库");
            updateById(order);
        }

        return Result.success();
    }

    private String generateOrderNo(String prefix) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        wrapper.likeRight("order_no", prefix + dateStr);
        wrapper.orderByDesc("order_no");
        wrapper.last("limit 1");
        SaleOrder last = baseMapper.selectOne(wrapper);
        int seq = 1;
        if (last != null) {
            String lastNo = last.getOrderNo();
            seq = Integer.parseInt(lastNo.substring(lastNo.length() - 3)) + 1;
        }
        return prefix + dateStr + String.format("%03d", seq);
    }
}

package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxc.common.Result;
import com.jxc.entity.InventoryLog;
import com.jxc.entity.Product;
import com.jxc.entity.PurchaseOrder;
import com.jxc.entity.PurchaseOrderDetail;
import com.jxc.mapper.PurchaseOrderMapper;
import com.jxc.service.InventoryLogService;
import com.jxc.service.ProductService;
import com.jxc.service.PurchaseOrderDetailService;
import com.jxc.service.PurchaseOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder> implements PurchaseOrderService {

    @Autowired
    private PurchaseOrderDetailService purchaseOrderDetailService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryLogService inventoryLogService;

    @Override
    @Transactional
    public Result createOrder(PurchaseOrder order, List<PurchaseOrderDetail> details) {
        String orderNo = generateOrderNo("CG");
        order.setOrderNo(orderNo);
        order.setStatus("待入库");
        if (order.getPurchaseDate() == null) {
            order.setPurchaseDate(LocalDate.now());
        }
        save(order);

        for (PurchaseOrderDetail detail : details) {
            detail.setOrderId(order.getId());
            if (detail.getReceivedQuantity() == null) {
                detail.setReceivedQuantity(0);
            }
        }
        purchaseOrderDetailService.saveBatch(details);

        return Result.success();
    }

    @Override
    @Transactional
    public Result updateOrder(PurchaseOrder order, List<PurchaseOrderDetail> details) {
        PurchaseOrder dbOrder = getById(order.getId());
        if (!"待入库".equals(dbOrder.getStatus())) {
            throw new RuntimeException("仅待入库状态的订单可编辑");
        }
        updateById(order);

        QueryWrapper<PurchaseOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", order.getId());
        purchaseOrderDetailService.remove(wrapper);

        for (PurchaseOrderDetail detail : details) {
            detail.setOrderId(order.getId());
            if (detail.getReceivedQuantity() == null) {
                detail.setReceivedQuantity(0);
            }
        }
        purchaseOrderDetailService.saveBatch(details);

        return Result.success();
    }

    @Override
    @Transactional
    public Result receiveStock(Long orderId, List<PurchaseOrderDetail> receiveDetails) {
        PurchaseOrder order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("采购订单不存在");
        }
        String status = order.getStatus();
        if (!"待入库".equals(status) && !"部分入库".equals(status)) {
            throw new RuntimeException("当前订单状态不允许入库操作");
        }

        QueryWrapper<PurchaseOrderDetail> detailWrapper = new QueryWrapper<>();
        detailWrapper.eq("order_id", orderId);
        List<PurchaseOrderDetail> originDetails = purchaseOrderDetailService.list(detailWrapper);

        for (PurchaseOrderDetail receiveDetail : receiveDetails) {
            PurchaseOrderDetail originDetail = null;
            for (PurchaseOrderDetail od : originDetails) {
                if (od.getProductId().equals(receiveDetail.getProductId())) {
                    originDetail = od;
                    break;
                }
            }
            if (originDetail == null) {
                throw new RuntimeException("未找到对应的原始明细");
            }

            int receivedQty = receiveDetail.getReceivedQuantity() == null ? 0 : receiveDetail.getReceivedQuantity();
            if (receivedQty <= 0) {
                throw new RuntimeException("本次入库数量必须大于0");
            }
            int maxReceive = originDetail.getQuantity() - originDetail.getReceivedQuantity();
            if (receivedQty > maxReceive) {
                throw new RuntimeException("本次入库数量超出可入库数量");
            }

            originDetail.setReceivedQuantity(originDetail.getReceivedQuantity() + receivedQty);
            purchaseOrderDetailService.updateById(originDetail);

            Product product = productService.getById(originDetail.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在");
            }
            product.setStockQuantity(product.getStockQuantity() + receivedQty);
            productService.updateById(product);

            InventoryLog log = new InventoryLog();
            log.setProductId(originDetail.getProductId());
            log.setChangeType("入库");
            log.setChangeQuantity(receivedQty);
            log.setAfterStock(product.getStockQuantity());
            log.setRelatedOrderNo(order.getOrderNo());
            log.setRemark("");
            inventoryLogService.save(log);
        }

        // 刷新明细列表，检查是否全部入库
        List<PurchaseOrderDetail> updatedDetails = purchaseOrderDetailService.list(detailWrapper);
        boolean allReceived = true;
        for (PurchaseOrderDetail detail : updatedDetails) {
            if (detail.getReceivedQuantity() < detail.getQuantity()) {
                allReceived = false;
                break;
            }
        }
        if (allReceived) {
            order.setStatus("已入库");
            updateById(order);
        }

        return Result.success();
    }

    private String generateOrderNo(String prefix) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<>();
        wrapper.likeRight("order_no", prefix + dateStr);
        wrapper.orderByDesc("order_no");
        wrapper.last("limit 1");
        PurchaseOrder last = baseMapper.selectOne(wrapper);
        int seq = 1;
        if (last != null) {
            String lastNo = last.getOrderNo();
            seq = Integer.parseInt(lastNo.substring(lastNo.length() - 3)) + 1;
        }
        return prefix + dateStr + String.format("%03d", seq);
    }
}

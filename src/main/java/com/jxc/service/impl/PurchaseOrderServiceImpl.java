package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxc.common.OrderNoUtils;
import com.jxc.common.OrderStatus;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder> implements PurchaseOrderService {

    public static final String STATUS_PENDING = OrderStatus.PURCHASE_PENDING;
    public static final String STATUS_PARTIAL = OrderStatus.PURCHASE_PARTIAL;
    public static final String STATUS_DONE = OrderStatus.PURCHASE_DONE;
    public static final String STATUS_CANCELED = OrderStatus.CANCELED;

    @Autowired
    private PurchaseOrderDetailService purchaseOrderDetailService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryLogService inventoryLogService;

    @Override
    @Transactional
    public Result createOrder(PurchaseOrder order, List<PurchaseOrderDetail> details) {
        if (details == null || details.isEmpty()) {
            throw new RuntimeException("请至少录入一条采购明细");
        }
        order.setStatus(STATUS_PENDING);
        if (order.getPurchaseDate() == null) {
            order.setPurchaseDate(LocalDate.now());
        }
        saveWithGeneratedNo(order);

        for (PurchaseOrderDetail detail : details) {
            detail.setId(null);
            detail.setOrderId(order.getId());
            detail.setReceivedQuantity(0);
        }
        purchaseOrderDetailService.saveBatch(details);

        return Result.success();
    }

    @Override
    @Transactional
    public Result updateOrder(PurchaseOrder order, List<PurchaseOrderDetail> details) {
        if (order.getId() == null) {
            throw new RuntimeException("缺少订单ID");
        }
        PurchaseOrder dbOrder = getById(order.getId());
        if (dbOrder == null) {
            throw new RuntimeException("采购订单不存在");
        }
        if (!STATUS_PENDING.equals(dbOrder.getStatus())) {
            throw new RuntimeException("仅待入库状态的订单可编辑");
        }
        if (details == null || details.isEmpty()) {
            throw new RuntimeException("请至少录入一条采购明细");
        }
        // 不允许前端篡改状态/单号
        order.setStatus(STATUS_PENDING);
        order.setOrderNo(null);
        updateById(order);

        QueryWrapper<PurchaseOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", order.getId());
        purchaseOrderDetailService.remove(wrapper);

        for (PurchaseOrderDetail detail : details) {
            detail.setId(null);
            detail.setOrderId(order.getId());
            detail.setReceivedQuantity(0);
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
        if (!STATUS_PENDING.equals(order.getStatus()) && !STATUS_PARTIAL.equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态不允许入库操作");
        }
        if (receiveDetails == null || receiveDetails.isEmpty()) {
            throw new RuntimeException("请至少录入一条入库明细");
        }

        QueryWrapper<PurchaseOrderDetail> detailWrapper = new QueryWrapper<>();
        detailWrapper.eq("order_id", orderId);
        List<PurchaseOrderDetail> originDetails = purchaseOrderDetailService.list(detailWrapper);

        // 支持按明细ID精确匹配，避免同一订单出现重复商品时串量
        Map<Long, PurchaseOrderDetail> detailById = new HashMap<>();
        for (PurchaseOrderDetail od : originDetails) {
            detailById.put(od.getId(), od);
        }
        Set<Long> handledDetailIds = new HashSet<>();

        for (PurchaseOrderDetail receiveDetail : receiveDetails) {
            PurchaseOrderDetail originDetail = matchDetail(originDetails, detailById, receiveDetail);
            if (!handledDetailIds.add(originDetail.getId())) {
                throw new RuntimeException("同一条入库明细被重复提交");
            }

            int receivedQty = receiveDetail.getReceivedQuantity() == null ? 0 : receiveDetail.getReceivedQuantity();
            if (receivedQty <= 0) {
                throw new RuntimeException("本次入库数量必须大于0");
            }
            int alreadyReceived = originDetail.getReceivedQuantity() == null ? 0 : originDetail.getReceivedQuantity();
            int maxReceive = originDetail.getQuantity() - alreadyReceived;
            if (receivedQty > maxReceive) {
                throw new RuntimeException("本次入库数量超出可入库数量（最多还可入库 " + maxReceive + "）");
            }

            originDetail.setReceivedQuantity(alreadyReceived + receivedQty);
            purchaseOrderDetailService.updateById(originDetail);

            Product product = productService.getById(originDetail.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在");
            }
            int stock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            product.setStockQuantity(stock + receivedQty);
            if (!productService.updateById(product)) {
                throw new RuntimeException("商品【" + product.getName() + "】库存已被其他操作修改，请重试");
            }

            InventoryLog log = new InventoryLog();
            log.setProductId(originDetail.getProductId());
            log.setChangeType("入库");
            log.setChangeQuantity(receivedQty);
            log.setAfterStock(product.getStockQuantity());
            log.setRelatedOrderNo(order.getOrderNo());
            log.setRemark("");
            inventoryLogService.save(log);
        }

        // 刷新明细列表，判断是部分入库还是全部入库
        List<PurchaseOrderDetail> updatedDetails = purchaseOrderDetailService.list(detailWrapper);
        boolean allReceived = true;
        for (PurchaseOrderDetail detail : updatedDetails) {
            int received = detail.getReceivedQuantity() == null ? 0 : detail.getReceivedQuantity();
            if (received < detail.getQuantity()) {
                allReceived = false;
                break;
            }
        }
        order.setStatus(allReceived ? STATUS_DONE : STATUS_PARTIAL);
        updateById(order);

        return Result.success();
    }

    @Override
    @Transactional
    public Result cancelOrder(Long orderId) {
        PurchaseOrder order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("采购订单不存在");
        }
        if (!STATUS_PENDING.equals(order.getStatus())) {
            throw new RuntimeException("仅待入库状态的订单可取消（已发生入库的订单不能取消）");
        }
        order.setStatus(STATUS_CANCELED);
        updateById(order);
        return Result.success();
    }

    /** 按明细ID匹配；未传ID时退化为按商品ID匹配（兼容旧前端） */
    private PurchaseOrderDetail matchDetail(List<PurchaseOrderDetail> originDetails,
                                            Map<Long, PurchaseOrderDetail> detailById,
                                            PurchaseOrderDetail receiveDetail) {
        if (receiveDetail.getId() != null) {
            PurchaseOrderDetail detail = detailById.get(receiveDetail.getId());
            if (detail == null) {
                throw new RuntimeException("未找到对应的原始明细（id=" + receiveDetail.getId() + "）");
            }
            return detail;
        }
        if (receiveDetail.getProductId() == null) {
            throw new RuntimeException("入库明细缺少商品");
        }
        for (PurchaseOrderDetail od : originDetails) {
            if (od.getProductId().equals(receiveDetail.getProductId())) {
                return od;
            }
        }
        throw new RuntimeException("未找到对应的原始明细");
    }

    /** 生成单号并落库；极端并发下若撞唯一键则自动重试 */
    private void saveWithGeneratedNo(PurchaseOrder order) {
        for (int attempt = 1; attempt <= 5; attempt++) {
            order.setOrderNo(generateOrderNo());
            try {
                save(order);
                return;
            } catch (DuplicateKeyException e) {
                order.setId(null);
                if (attempt == 5) {
                    throw new RuntimeException("订单号生成冲突，请重试");
                }
            }
        }
    }

    private String generateOrderNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<>();
        wrapper.likeRight("order_no", "CG" + dateStr);
        wrapper.orderByDesc("order_no");
        wrapper.last("limit 1");
        PurchaseOrder last = baseMapper.selectOne(wrapper);
        return OrderNoUtils.next("CG", dateStr, last == null ? null : last.getOrderNo());
    }
}

package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxc.common.OrderNoUtils;
import com.jxc.common.OrderStatus;
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
public class SaleOrderServiceImpl extends ServiceImpl<SaleOrderMapper, SaleOrder> implements SaleOrderService {

    public static final String STATUS_PENDING = OrderStatus.SALE_PENDING;
    public static final String STATUS_PARTIAL = OrderStatus.SALE_PARTIAL;
    public static final String STATUS_DONE = OrderStatus.SALE_DONE;
    public static final String STATUS_CANCELED = OrderStatus.CANCELED;

    @Autowired
    private SaleOrderDetailService saleOrderDetailService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryLogService inventoryLogService;

    @Override
    @Transactional
    public Result createOrder(SaleOrder order, List<SaleOrderDetail> details) {
        if (details == null || details.isEmpty()) {
            throw new RuntimeException("请至少录入一条销售明细");
        }
        order.setStatus(STATUS_PENDING);
        if (order.getSaleDate() == null) {
            order.setSaleDate(LocalDate.now());
        }
        saveWithGeneratedNo(order);

        for (SaleOrderDetail detail : details) {
            detail.setId(null);
            detail.setOrderId(order.getId());
            detail.setShippedQuantity(0);
        }
        saleOrderDetailService.saveBatch(details);

        return Result.success();
    }

    @Override
    @Transactional
    public Result updateOrder(SaleOrder order, List<SaleOrderDetail> details) {
        if (order.getId() == null) {
            throw new RuntimeException("缺少订单ID");
        }
        SaleOrder dbOrder = getById(order.getId());
        if (dbOrder == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (!STATUS_PENDING.equals(dbOrder.getStatus())) {
            throw new RuntimeException("仅待出库状态的订单可编辑");
        }
        if (details == null || details.isEmpty()) {
            throw new RuntimeException("请至少录入一条销售明细");
        }
        order.setStatus(STATUS_PENDING);
        order.setOrderNo(null);
        updateById(order);

        QueryWrapper<SaleOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", order.getId());
        saleOrderDetailService.remove(wrapper);

        for (SaleOrderDetail detail : details) {
            detail.setId(null);
            detail.setOrderId(order.getId());
            detail.setShippedQuantity(0);
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
        if (!STATUS_PENDING.equals(order.getStatus()) && !STATUS_PARTIAL.equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态不允许出库操作");
        }
        if (shipDetails == null || shipDetails.isEmpty()) {
            throw new RuntimeException("请至少录入一条出库明细");
        }

        QueryWrapper<SaleOrderDetail> detailWrapper = new QueryWrapper<>();
        detailWrapper.eq("order_id", orderId);
        List<SaleOrderDetail> originDetails = saleOrderDetailService.list(detailWrapper);

        // 支持按明细ID精确匹配，避免同一订单出现重复商品时串量
        Map<Long, SaleOrderDetail> detailById = new HashMap<>();
        for (SaleOrderDetail od : originDetails) {
            detailById.put(od.getId(), od);
        }
        Set<Long> handledDetailIds = new HashSet<>();

        for (SaleOrderDetail shipDetail : shipDetails) {
            SaleOrderDetail originDetail = matchDetail(originDetails, detailById, shipDetail);
            if (!handledDetailIds.add(originDetail.getId())) {
                throw new RuntimeException("同一条出库明细被重复提交");
            }

            int shippedQty = shipDetail.getShippedQuantity() == null ? 0 : shipDetail.getShippedQuantity();
            if (shippedQty <= 0) {
                throw new RuntimeException("本次出库数量必须大于0");
            }
            int alreadyShipped = originDetail.getShippedQuantity() == null ? 0 : originDetail.getShippedQuantity();
            int maxShip = originDetail.getQuantity() - alreadyShipped;
            if (shippedQty > maxShip) {
                throw new RuntimeException("本次出库数量超出可出库数量（最多还可出库 " + maxShip + "）");
            }

            Product product = productService.getById(originDetail.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在");
            }
            int stock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            if (stock < shippedQty) {
                throw new RuntimeException("商品【" + product.getName() + "】库存不足，当前库存：" + stock
                        + "，需要出库：" + shippedQty + "，缺货数量：" + (shippedQty - stock));
            }

            originDetail.setShippedQuantity(alreadyShipped + shippedQty);
            saleOrderDetailService.updateById(originDetail);

            product.setStockQuantity(stock - shippedQty);
            if (!productService.updateById(product)) {
                throw new RuntimeException("商品【" + product.getName() + "】库存已被其他操作修改，请重试");
            }

            InventoryLog log = new InventoryLog();
            log.setProductId(originDetail.getProductId());
            log.setChangeType("出库");
            log.setChangeQuantity(shippedQty);
            log.setAfterStock(product.getStockQuantity());
            log.setRelatedOrderNo(order.getOrderNo());
            log.setRemark("");
            inventoryLogService.save(log);
        }

        // 刷新明细列表，判断是部分出库还是全部出库
        List<SaleOrderDetail> updatedDetails = saleOrderDetailService.list(detailWrapper);
        boolean allShipped = true;
        for (SaleOrderDetail detail : updatedDetails) {
            int shipped = detail.getShippedQuantity() == null ? 0 : detail.getShippedQuantity();
            if (shipped < detail.getQuantity()) {
                allShipped = false;
                break;
            }
        }
        order.setStatus(allShipped ? STATUS_DONE : STATUS_PARTIAL);
        updateById(order);

        return Result.success();
    }

    @Override
    @Transactional
    public Result cancelOrder(Long orderId) {
        SaleOrder order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (!STATUS_PENDING.equals(order.getStatus())) {
            throw new RuntimeException("仅待出库状态的订单可取消（已发生出库的订单不能取消）");
        }
        order.setStatus(STATUS_CANCELED);
        updateById(order);
        return Result.success();
    }

    /** 按明细ID匹配；未传ID时退化为按商品ID匹配（兼容旧前端） */
    private SaleOrderDetail matchDetail(List<SaleOrderDetail> originDetails,
                                        Map<Long, SaleOrderDetail> detailById,
                                        SaleOrderDetail shipDetail) {
        if (shipDetail.getId() != null) {
            SaleOrderDetail detail = detailById.get(shipDetail.getId());
            if (detail == null) {
                throw new RuntimeException("未找到对应的原始明细（id=" + shipDetail.getId() + "）");
            }
            return detail;
        }
        if (shipDetail.getProductId() == null) {
            throw new RuntimeException("出库明细缺少商品");
        }
        for (SaleOrderDetail od : originDetails) {
            if (od.getProductId().equals(shipDetail.getProductId())) {
                return od;
            }
        }
        throw new RuntimeException("未找到对应的原始明细");
    }

    /** 生成单号并落库；极端并发下若撞唯一键则自动重试 */
    private void saveWithGeneratedNo(SaleOrder order) {
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
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        wrapper.likeRight("order_no", "XS" + dateStr);
        wrapper.orderByDesc("order_no");
        wrapper.last("limit 1");
        SaleOrder last = baseMapper.selectOne(wrapper);
        return OrderNoUtils.next("XS", dateStr, last == null ? null : last.getOrderNo());
    }
}

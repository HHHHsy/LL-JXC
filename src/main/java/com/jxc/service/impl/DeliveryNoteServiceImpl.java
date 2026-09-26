package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.MoneyUpper;
import com.jxc.dto.DeliveryNote;
import com.jxc.dto.DeliveryNoteItem;
import com.jxc.entity.Customer;
import com.jxc.entity.Product;
import com.jxc.entity.SaleOrder;
import com.jxc.entity.SaleOrderDetail;
import com.jxc.service.ConfigService;
import com.jxc.service.CustomerService;
import com.jxc.service.DeliveryNoteService;
import com.jxc.service.ProductService;
import com.jxc.service.SaleOrderDetailService;
import com.jxc.service.SaleOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 送货单数据组装。
 *
 * <p>抬头等固定信息来自系统设置（界面「系统设置 → 送货单设置」保存到 sys_config，
 * 未配置时回落到 application.yml 的 jxc.delivery-note.*）；客户信息取客户档案；
 * 明细取销售订单明细并关联商品的名称/规格/单位。</p>
 */
@Service
public class DeliveryNoteServiceImpl implements DeliveryNoteService {

    private static final String GROUP = "delivery-note";

    @Autowired
    private SaleOrderService saleOrderService;

    @Autowired
    private SaleOrderDetailService saleOrderDetailService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ConfigService configService;

    @Override
    public DeliveryNote buildForSaleOrder(Long orderId) {
        SaleOrder order = saleOrderService.getById(orderId);
        if (order == null) {
            throw new RuntimeException("销售订单不存在");
        }
        Map<String, String> cfg = configService.getGroup(GROUP);

        DeliveryNote note = new DeliveryNote();
        note.setCompanyName(cfg.getOrDefault("companyName", ""));
        note.setTitle(value(cfg.get("title"), "送货单"));
        note.setDeliveryMan(cfg.getOrDefault("deliveryMan", ""));
        note.setServicePhone(cfg.getOrDefault("servicePhone", ""));
        note.setServiceAddress(cfg.getOrDefault("serviceAddress", ""));
        note.setFooterNote(cfg.getOrDefault("footerNote", ""));
        note.setOrderNo(order.getOrderNo());
        note.setSaleDate(order.getSaleDate());
        note.setOrderStatus(order.getStatus());

        // 单据顶部的「电话 / 地址」只取客户档案：客户没填写就保持空白，
        // 绝不用本店服务电话或任何示例号码兜底（服务电话只出现在页脚）。
        Customer customer = order.getCustomerId() == null ? null : customerService.getById(order.getCustomerId());
        if (customer != null) {
            note.setCustomerName(customer.getName());
            note.setCustomerAddress(customer.getAddress());
            note.setCustomerPhone(customer.getPhone());
        }

        QueryWrapper<SaleOrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", orderId);
        wrapper.orderByAsc("id");
        List<SaleOrderDetail> details = saleOrderDetailService.list(wrapper);

        Set<Long> productIds = details.stream().map(SaleOrderDetail::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Product> products = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Product p : productService.listByIds(productIds)) {
                products.put(p.getId(), p);
            }
        }

        List<DeliveryNoteItem> items = new ArrayList<>();
        int totalQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        int seq = 1;
        for (SaleOrderDetail detail : details) {
            Product product = products.get(detail.getProductId());
            DeliveryNoteItem item = new DeliveryNoteItem();
            item.setSeq(seq++);
            item.setProductName(product != null ? product.getName() : "");
            item.setSpec(product != null ? product.getSpec() : "");
            item.setUnit(product != null ? product.getUnit() : "");
            item.setQuantity(detail.getQuantity());
            item.setPrice(detail.getSalePrice() == null ? BigDecimal.ZERO : detail.getSalePrice());
            item.setRemark(detail.getRemark() == null ? "" : detail.getRemark());
            BigDecimal amount = item.getPrice()
                    .multiply(BigDecimal.valueOf(detail.getQuantity() == null ? 0 : detail.getQuantity()));
            item.setAmount(amount);
            items.add(item);

            totalQuantity += detail.getQuantity() == null ? 0 : detail.getQuantity();
            totalAmount = totalAmount.add(amount);
        }

        // 明细不足时补空行，与纸质单据行数对齐
        int minRows = parsePositive(cfg.get("minRows"), 4);
        for (int i = items.size(); i < minRows; i++) {
            DeliveryNoteItem blank = new DeliveryNoteItem();
            blank.setSeq(i + 1);
            blank.setBlank(true);
            items.add(blank);
        }

        note.setItems(items);
        note.setTotalQuantity(totalQuantity);
        note.setTotalAmount(totalAmount);
        note.setTotalAmountUpper(MoneyUpper.toUpper(totalAmount));
        return note;
    }

    private String value(String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    private int parsePositive(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

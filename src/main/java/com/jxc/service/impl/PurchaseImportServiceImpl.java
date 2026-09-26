package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.ExcelSupport;
import com.jxc.dto.PurchaseImportPreview;
import com.jxc.dto.PurchaseImportResult;
import com.jxc.dto.PurchaseImportRow;
import com.jxc.entity.Product;
import com.jxc.entity.PurchaseOrder;
import com.jxc.entity.PurchaseOrderDetail;
import com.jxc.service.ProductService;
import com.jxc.service.PurchaseImportService;
import com.jxc.service.PurchaseOrderService;
import org.apache.poi.ss.usermodel.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 采购清单 Excel 导入实现（通用解析逻辑在 {@link ExcelSupport}）。
 *
 * <p>设计要点：按表头中文名定位列；自动跳过空行与「合计/总计」汇总行；
 * 商品按「名称+规格」匹配，其次按名称；未匹配到时可自动建档；「参考适合车型」写入明细备注。</p>
 */
@Service
public class PurchaseImportServiceImpl implements PurchaseImportService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseImportServiceImpl.class);

    private static final List<ExcelSupport.Column> COLUMNS = List.of(
            new ExcelSupport.Column("seq", "序号", false, "序号"),
            new ExcelSupport.Column("name", "采购型号名称", true, "型号名称", "商品名称", "品名", "货物名称"),
            new ExcelSupport.Column("spec", "参考规格型号", false, "规格"),
            new ExcelSupport.Column("unit", "单位", false, "单位"),
            new ExcelSupport.Column("qty", "数量", true, "数量"),
            new ExcelSupport.Column("price", "单价", false, "单价", "价格"),
            new ExcelSupport.Column("amount", "金额", false, "金额", "小计"),
            new ExcelSupport.Column("car", "参考适合车型", false, "车型")
    );

    private static final String CODE_PREFIX = "SP";
    private static final Pattern CODE_PATTERN = Pattern.compile("^" + CODE_PREFIX + "(\\d+)$");

    @Autowired
    private ProductService productService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    // ------------------------------------------------------------------ 解析

    @Override
    public PurchaseImportPreview parse(MultipartFile file, boolean autoCreateProduct) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请选择要导入的 Excel 文件");
        }
        String fileName = file.getOriginalFilename() == null ? "未命名文件" : file.getOriginalFilename();
        try (InputStream in = file.getInputStream(); ExcelSupport.Reader reader = ExcelSupport.Reader.open(in)) {
            PurchaseImportPreview preview = new PurchaseImportPreview();
            preview.setFileName(fileName);
            preview.setSheetName(reader.sheetName());
            preview.setTotalRows(reader.lastRowIndex() + 1);

            ExcelSupport.Header header = ExcelSupport.locateHeader(reader, COLUMNS, 10);
            ProductIndex index = ProductIndex.load(productService);

            for (int i = header.getHeaderRowIndex() + 1; i <= reader.lastRowIndex(); i++) {
                Row row = reader.row(i);
                if (row == null) {
                    continue;
                }
                int rowNo = i + 1;
                String name = reader.text(row, header.col("name"));
                if (name.isEmpty()) {
                    String joined = reader.join(row);
                    if (!joined.isEmpty()) {
                        preview.setSkippedRows(preview.getSkippedRows() + 1);
                        preview.getSkipped().add("第 " + rowNo + " 行："
                                + (ExcelSupport.isSummaryRow(joined) ? "合计/汇总行" : "无商品名称") + "，已跳过");
                    }
                    continue;
                }
                preview.setDataRows(preview.getDataRows() + 1);

                PurchaseImportRow parsed = new PurchaseImportRow();
                parsed.setRowNo(rowNo);
                parsed.setSeq(reader.text(row, header.col("seq")));
                parsed.setProductName(name);
                parsed.setSpec(reader.text(row, header.col("spec")));
                parsed.setUnit(reader.text(row, header.col("unit")));
                parsed.setCarModels(reader.text(row, header.col("car")));

                BigDecimal qtyValue = reader.decimal(row, header.col("qty"));
                if (qtyValue == null || qtyValue.signum() <= 0) {
                    parsed.setStatus("ERROR");
                    parsed.setMessage("数量为空或不是大于 0 的数字");
                } else if (qtyValue.stripTrailingZeros().scale() > 0) {
                    // 不做四舍五入，避免把 2.5 悄悄改成 3
                    parsed.setStatus("ERROR");
                    parsed.setMessage("数量必须是整数，当前为 " + qtyValue.stripTrailingZeros().toPlainString());
                } else {
                    parsed.setQuantity(qtyValue.intValue());
                    if (parsed.getQuantity() <= 0) {
                        parsed.setStatus("ERROR");
                        parsed.setMessage("数量小于 1");
                    }
                }

                if (header.col("price") != null) {
                    BigDecimal price = reader.decimal(row, header.col("price"));
                    if (price == null) {
                        setError(parsed, "单价为空或不是数字");
                    } else if (price.signum() < 0) {
                        setError(parsed, "单价不能为负数");
                    } else {
                        parsed.setUnitPrice(price.setScale(2, RoundingMode.HALF_UP));
                    }
                }
                if (header.col("amount") != null) {
                    parsed.setAmount(reader.decimal(row, header.col("amount")));
                }

                if (!"ERROR".equals(parsed.getStatus())) {
                    matchProduct(parsed, index, autoCreateProduct);
                }
                checkAmount(parsed);

                if ("ERROR".equals(parsed.getStatus())) {
                    preview.setErrorRows(preview.getErrorRows() + 1);
                } else {
                    if ("NEW".equals(parsed.getStatus())) {
                        preview.setNewProductRows(preview.getNewProductRows() + 1);
                    } else {
                        preview.setMatchedRows(preview.getMatchedRows() + 1);
                    }
                    preview.setTotalQuantity(preview.getTotalQuantity() + nvl(parsed.getQuantity()));
                    preview.setTotalAmount(preview.getTotalAmount().add(rowAmount(parsed)));
                }
                preview.getRows().add(parsed);
            }

            preview.setTotalAmount(preview.getTotalAmount().setScale(2, RoundingMode.HALF_UP));
            preview.setImportable(preview.getErrorRows() == 0 && !preview.getRows().isEmpty());
            if (preview.getRows().isEmpty()) {
                throw new RuntimeException("清单中没有可导入的明细行，请检查文件内容");
            }
            return preview;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析采购清单失败: {}", e.getMessage(), e);
            throw new RuntimeException("Excel 解析失败，请确认文件为 .xlsx 或 .xls 格式且未损坏");
        }
    }

    private void setError(PurchaseImportRow row, String message) {
        row.setStatus("ERROR");
        row.setMessage(row.getMessage() == null ? message : row.getMessage() + "；" + message);
    }

    // ------------------------------------------------------------------ 匹配

    private void matchProduct(PurchaseImportRow row, ProductIndex index, boolean autoCreate) {
        Product byNameSpec = index.byNameSpec.get(ExcelSupport.key(row.getProductName(), row.getSpec()));
        if (byNameSpec != null) {
            fillMatched(row, byNameSpec);
            return;
        }
        List<Product> sameName = index.byName.get(ExcelSupport.key(row.getProductName()));
        if (sameName != null && sameName.size() == 1) {
            fillMatched(row, sameName.get(0));
            return;
        }
        if (sameName != null && sameName.size() > 1) {
            row.setStatus("ERROR");
            row.setMessage("系统中有 " + sameName.size() + " 个同名商品，请在清单中补充「参考规格型号」以便区分");
            return;
        }
        if (autoCreate) {
            row.setStatus("NEW");
            row.setMessage("系统中不存在，导入时将自动建档");
        } else {
            row.setStatus("ERROR");
            row.setMessage("系统中不存在该商品，且未开启自动建档");
        }
    }

    private void fillMatched(PurchaseImportRow row, Product product) {
        row.setMatchedProductId(product.getId());
        row.setMatchedProductCode(product.getCode());
        row.setStatus("OK");
    }

    private void checkAmount(PurchaseImportRow row) {
        if (row.getAmount() == null || row.getQuantity() == null || row.getUnitPrice() == null) {
            return;
        }
        BigDecimal expect = row.getUnitPrice().multiply(BigDecimal.valueOf(row.getQuantity()));
        if (expect.subtract(row.getAmount()).abs().compareTo(new BigDecimal("0.01")) > 0) {
            row.setAmountMismatch(true);
            String tip = "清单金额 " + row.getAmount().stripTrailingZeros().toPlainString()
                    + " 与 数量×单价 " + expect.stripTrailingZeros().toPlainString() + " 不一致（以单价为准）";
            row.setMessage(row.getMessage() == null ? tip : row.getMessage() + "；" + tip);
        }
    }

    private BigDecimal rowAmount(PurchaseImportRow row) {
        if (row.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal price = row.getUnitPrice() == null ? BigDecimal.ZERO : row.getUnitPrice();
        return price.multiply(BigDecimal.valueOf(row.getQuantity()));
    }

    /** 商品索引：按「名称」与「名称+规格」建索引 */
    private static final class ProductIndex {
        private final Map<String, List<Product>> byName = new HashMap<>();
        private final Map<String, Product> byNameSpec = new HashMap<>();

        static ProductIndex load(ProductService productService) {
            ProductIndex index = new ProductIndex();
            for (Product p : productService.list()) {
                index.byName.computeIfAbsent(ExcelSupport.key(p.getName()), k -> new ArrayList<>()).add(p);
                String spec = p.getSpec() == null ? "" : p.getSpec();
                if (!spec.trim().isEmpty()) {
                    index.byNameSpec.putIfAbsent(ExcelSupport.key(p.getName(), spec), p);
                }
            }
            return index;
        }
    }

    // ------------------------------------------------------------------ 导入

    @Override
    @Transactional
    public PurchaseImportResult importOrder(MultipartFile file, Long supplierId, LocalDate purchaseDate,
                                            boolean autoCreateProduct, boolean skipErrorRows) {
        if (supplierId == null) {
            throw new RuntimeException("请选择供应商");
        }
        PurchaseImportPreview preview = parse(file, autoCreateProduct);

        List<PurchaseImportRow> usable = new ArrayList<>();
        int skipped = preview.getSkippedRows();
        for (PurchaseImportRow row : preview.getRows()) {
            if ("ERROR".equals(row.getStatus())) {
                if (!skipErrorRows) {
                    throw new RuntimeException("清单第 " + row.getRowNo() + " 行有问题：" + row.getMessage()
                            + "（可在预览中查看全部问题行，或勾选「忽略有问题的行」后重试）");
                }
                skipped++;
                continue;
            }
            usable.add(row);
        }
        if (usable.isEmpty()) {
            throw new RuntimeException("没有可导入的明细行");
        }

        // 1) 未匹配到的商品自动建档（同一名称+规格只建一次）
        Map<String, Long> createdByKey = new HashMap<>();
        Set<String> existingCodes = loadProductCodes();
        int createdCount = 0;
        for (PurchaseImportRow row : usable) {
            if (!"NEW".equals(row.getStatus())) {
                continue;
            }
            String k = ExcelSupport.key(row.getProductName(), row.getSpec());
            Long productId = createdByKey.get(k);
            if (productId == null) {
                Product product = new Product();
                product.setCode(nextProductCode(existingCodes));
                product.setName(row.getProductName());
                product.setSpec(row.getSpec());
                product.setUnit(row.getUnit());
                product.setCostPrice(row.getUnitPrice() == null ? BigDecimal.ZERO : row.getUnitPrice());
                product.setSalePrice(BigDecimal.ZERO);
                product.setStockQuantity(0);
                productService.save(product);
                existingCodes.add(product.getCode());
                productId = product.getId();
                createdByKey.put(k, productId);
                createdCount++;
            }
            row.setMatchedProductId(productId);
        }

        // 2) 组装订单与明细
        PurchaseOrder order = new PurchaseOrder();
        order.setSupplierId(supplierId);
        order.setPurchaseDate(purchaseDate == null ? LocalDate.now() : purchaseDate);

        List<PurchaseOrderDetail> details = new ArrayList<>();
        int totalQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseImportRow row : usable) {
            PurchaseOrderDetail detail = new PurchaseOrderDetail();
            detail.setProductId(row.getMatchedProductId());
            detail.setQuantity(row.getQuantity());
            detail.setCostPrice(row.getUnitPrice() == null ? BigDecimal.ZERO : row.getUnitPrice());
            detail.setRemark(row.getCarModels() == null ? "" : row.getCarModels());
            details.add(detail);
            totalQuantity += nvl(row.getQuantity());
            totalAmount = totalAmount.add(rowAmount(row));
        }

        purchaseOrderService.createOrder(order, details);

        PurchaseImportResult result = new PurchaseImportResult();
        result.setOrderId(order.getId());
        result.setOrderNo(order.getOrderNo());
        result.setDetailCount(details.size());
        result.setCreatedProductCount(createdCount);
        result.setSkippedRowCount(skipped);
        result.setTotalQuantity(totalQuantity);
        result.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        log.info("采购清单导入完成：单号={} 明细={} 新建商品={} 跳过行={}",
                result.getOrderNo(), result.getDetailCount(), result.getCreatedProductCount(), skipped);
        return result;
    }

    private Set<String> loadProductCodes() {
        Set<String> codes = new HashSet<>();
        for (Object code : productService.listObjs(new QueryWrapper<Product>().select("code"))) {
            if (code != null) {
                codes.add(String.valueOf(code));
            }
        }
        return codes;
    }

    /** 生成下一个可用编码：沿用已有编码位数，没有则默认 4 位 */
    private String nextProductCode(Set<String> existingCodes) {
        int max = 0;
        int width = 4;
        for (String code : existingCodes) {
            Matcher m = CODE_PATTERN.matcher(code);
            if (m.matches()) {
                int value = Integer.parseInt(m.group(1));
                if (value >= max) {
                    max = value;
                    width = m.group(1).length();
                }
            }
        }
        int next = max + 1;
        String code = CODE_PREFIX + String.format("%0" + width + "d", next);
        while (existingCodes.contains(code)) {
            next++;
            code = CODE_PREFIX + String.format("%0" + width + "d", next);
        }
        return code;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}

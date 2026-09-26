package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.ExcelSupport;
import com.jxc.dto.ProductImportPreview;
import com.jxc.dto.ProductImportResult;
import com.jxc.dto.ProductImportRow;
import com.jxc.entity.Product;
import com.jxc.service.ProductImportService;
import com.jxc.service.ProductService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商品 Excel 导入实现（仅新增，不覆盖已有商品）。
 *
 * <p>列按表头中文名识别（见 {@link #COLUMNS}），支持列顺序调整；空行与「合计」行自动跳过。</p>
 */
@Service
public class ProductImportServiceImpl implements ProductImportService {

    private static final Logger log = LoggerFactory.getLogger(ProductImportServiceImpl.class);

    /** 支持的表头别名（顺序即优先级） */
    private static final List<ExcelSupport.Column> COLUMNS = List.of(
            new ExcelSupport.Column("code", "商品编码", false, "商品编码", "编码", "货号"),
            new ExcelSupport.Column("name", "商品名称", true, "商品名称", "商品名", "品名", "名称"),
            new ExcelSupport.Column("category", "商品分类", false, "商品分类", "分类", "类别"),
            new ExcelSupport.Column("spec", "规格型号", false, "规格型号", "规格", "型号"),
            new ExcelSupport.Column("unit", "单位", false, "单位"),
            new ExcelSupport.Column("costPrice", "成本价", false, "成本价", "进价", "采购价"),
            new ExcelSupport.Column("salePrice", "销售价", false, "销售价", "售价", "零售价")
    );

    private static final String CODE_PREFIX = "SP";
    private static final Pattern CODE_PATTERN = Pattern.compile("^" + CODE_PREFIX + "(\\d+)$");

    @Autowired
    private ProductService productService;

    @Override
    public ProductImportPreview parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请选择要导入的 Excel 文件");
        }
        String fileName = file.getOriginalFilename() == null ? "未命名文件" : file.getOriginalFilename();
        try (InputStream in = file.getInputStream(); ExcelSupport.Reader reader = ExcelSupport.Reader.open(in)) {
            ProductImportPreview preview = new ProductImportPreview();
            preview.setFileName(fileName);
            preview.setSheetName(reader.sheetName());
            preview.setTotalRows(reader.lastRowIndex() + 1);

            ExcelSupport.Header header = ExcelSupport.locateHeader(reader, COLUMNS, 10);

            Set<String> existingCodes = new HashSet<>();
            Map<String, Product> existingByKey = new HashMap<>();
            for (Product p : productService.list()) {
                existingCodes.add(p.getCode());
                existingByKey.putIfAbsent(ExcelSupport.key(p.getName(), p.getSpec()), p);
            }
            Set<String> codesInFile = new HashSet<>();
            Set<String> keysInFile = new HashSet<>();

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

                ProductImportRow parsed = new ProductImportRow();
                parsed.setRowNo(rowNo);
                parsed.setName(name);
                parsed.setCode(reader.text(row, header.col("code")));
                parsed.setCategory(reader.text(row, header.col("category")));
                parsed.setSpec(reader.text(row, header.col("spec")));
                parsed.setUnit(reader.text(row, header.col("unit")));

                BigDecimal cost = reader.decimal(row, header.col("costPrice"));
                BigDecimal sale = reader.decimal(row, header.col("salePrice"));
                if (cost != null && cost.signum() < 0) {
                    setError(parsed, "成本价不能为负数");
                } else {
                    parsed.setCostPrice(cost == null ? BigDecimal.ZERO : cost.setScale(2, RoundingMode.HALF_UP));
                }
                if (sale != null && sale.signum() < 0) {
                    setError(parsed, "销售价不能为负数");
                } else {
                    parsed.setSalePrice(sale == null ? BigDecimal.ZERO : sale.setScale(2, RoundingMode.HALF_UP));
                }

                String dupKey = ExcelSupport.key(parsed.getName(), parsed.getSpec());
                if (!parsed.getCode().isEmpty()) {
                    if (existingCodes.contains(parsed.getCode())) {
                        setError(parsed, "商品编码 " + parsed.getCode() + " 已存在");
                    } else if (!codesInFile.add(parsed.getCode())) {
                        setError(parsed, "文件内商品编码 " + parsed.getCode() + " 重复");
                    }
                }
                Product same = existingByKey.get(dupKey);
                if (same != null) {
                    setError(parsed, "已存在同名同规格商品（编码 " + same.getCode() + "）");
                } else if (!keysInFile.add(dupKey)) {
                    setError(parsed, "文件内「名称+规格」重复");
                }

                if (!"ERROR".equals(parsed.getStatus())) {
                    parsed.setStatus("NEW");
                    preview.setNewRows(preview.getNewRows() + 1);
                } else {
                    preview.setErrorRows(preview.getErrorRows() + 1);
                }
                preview.getRows().add(parsed);
            }

            preview.setImportable(preview.getErrorRows() == 0 && !preview.getRows().isEmpty());
            if (preview.getRows().isEmpty()) {
                throw new RuntimeException("表格中没有可导入的商品行，请检查文件内容");
            }
            return preview;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析商品 Excel 失败: {}", e.getMessage(), e);
            throw new RuntimeException("Excel 解析失败，请确认文件为 .xlsx 或 .xls 格式且未损坏");
        }
    }

    @Override
    @Transactional
    public ProductImportResult importProducts(MultipartFile file, boolean skipErrorRows) {
        ProductImportPreview preview = parse(file);

        List<ProductImportRow> usable = new ArrayList<>();
        int skipped = preview.getSkippedRows();
        for (ProductImportRow row : preview.getRows()) {
            if ("ERROR".equals(row.getStatus())) {
                if (!skipErrorRows) {
                    throw new RuntimeException("表格第 " + row.getRowNo() + " 行有问题：" + row.getMessage()
                            + "（可先查看预览，或勾选「忽略有问题的行」后重试）");
                }
                skipped++;
                continue;
            }
            usable.add(row);
        }
        if (usable.isEmpty()) {
            throw new RuntimeException("没有可导入的商品行");
        }

        Set<String> existingCodes = new HashSet<>();
        for (Object code : productService.listObjs(new QueryWrapper<Product>().select("code"))) {
            if (code != null) {
                existingCodes.add(String.valueOf(code));
            }
        }

        List<Product> products = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (ProductImportRow row : usable) {
            Product product = new Product();
            String code = row.getCode();
            if (code == null || code.isEmpty()) {
                code = nextProductCode(existingCodes);
                existingCodes.add(code);
            }
            product.setCode(code);
            product.setName(row.getName());
            product.setCategory(row.getCategory());
            product.setSpec(row.getSpec());
            product.setUnit(row.getUnit());
            product.setCostPrice(row.getCostPrice());
            product.setSalePrice(row.getSalePrice());
            product.setStockQuantity(0);
            products.add(product);
            codes.add(code);
        }
        productService.saveBatch(products);

        ProductImportResult result = new ProductImportResult();
        result.setCreatedCount(products.size());
        result.setSkippedRowCount(skipped);
        result.setCreatedCodes(codes);
        log.info("商品导入完成：新增 {} 个，跳过 {} 行", result.getCreatedCount(), skipped);
        return result;
    }

    private void setError(ProductImportRow row, String message) {
        row.setStatus("ERROR");
        row.setMessage(row.getMessage() == null ? message : row.getMessage() + "；" + message);
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
}

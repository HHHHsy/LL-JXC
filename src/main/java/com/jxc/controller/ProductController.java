package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jxc.common.ExcelTemplate;
import com.jxc.common.FileResponses;
import com.jxc.common.PageResult;
import com.jxc.common.Result;
import com.jxc.entity.InventoryLog;
import com.jxc.entity.Product;
import com.jxc.entity.PurchaseOrderDetail;
import com.jxc.entity.SaleOrderDetail;
import com.jxc.service.InventoryLogService;
import com.jxc.service.ProductImportService;
import com.jxc.service.ProductService;
import com.jxc.service.PurchaseOrderDetailService;
import com.jxc.service.SaleOrderDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductImportService productImportService;

    @Autowired
    private PurchaseOrderDetailService purchaseOrderDetailService;

    @Autowired
    private SaleOrderDetailService saleOrderDetailService;

    @Autowired
    private InventoryLogService inventoryLogService;

    /** 分页查询 */
    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String code,
                       @RequestParam(required = false) String name,
                       @RequestParam(required = false) String category,
                       @RequestParam(defaultValue = "1") long pageNum,
                       @RequestParam(defaultValue = "10") long pageSize) {
        QueryWrapper<Product> wrapper = buildWrapper(code, name, category);
        Page<Product> page = productService.page(new Page<>(pageNum, pageSize), wrapper);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /** 不分页：供下拉选择使用 */
    @GetMapping("/all")
    public Result all(@RequestParam(required = false) String code,
                      @RequestParam(required = false) String name,
                      @RequestParam(required = false) String category) {
        return Result.success(productService.list(buildWrapper(code, name, category)));
    }

    private QueryWrapper<Product> buildWrapper(String code, String name, String category) {
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        if (code != null && !code.isEmpty()) {
            wrapper.like("code", code);
        }
        if (name != null && !name.isEmpty()) {
            wrapper.like("name", name);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.like("category", category);
        }
        wrapper.orderByDesc("id");
        return wrapper;
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        Product product = productService.getById(id);
        if (product == null) {
            return Result.error("商品不存在");
        }
        return Result.success(product);
    }

    @PostMapping
    public Result save(@RequestBody Product product) {
        if (product.getCode() == null || product.getCode().trim().isEmpty()) {
            throw new RuntimeException("请输入商品编码");
        }
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new RuntimeException("请输入商品名称");
        }
        // 库存只能由入库/出库/盘点自动计算，禁止手工录入
        product.setId(null);
        product.setStockQuantity(0);
        product.setVersion(null);
        productService.save(product);
        return Result.success();
    }

    @PutMapping
    public Result updateById(@RequestBody Product product) {
        if (product.getId() == null) {
            throw new RuntimeException("缺少商品ID");
        }
        if (productService.getById(product.getId()) == null) {
            throw new RuntimeException("商品不存在");
        }
        // 库存与版本号不允许通过编辑接口修改
        product.setStockQuantity(null);
        product.setVersion(null);
        productService.updateById(product);
        return Result.success();
    }

    /**
     * 商品 Excel 导入：下载示例模板（表头与解析器保持一致）。
     */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() {
        return FileResponses.download(ExcelTemplate.productTemplate(), "商品导入示例.xlsx");
    }

    /**
     * 商品 Excel 导入：仅解析并校验，用于导入前预览。
     */
    @PostMapping("/import/preview")
    public Result previewImport(@RequestParam("file") MultipartFile file) {
        return Result.success(productImportService.parse(file));
    }

    /**
     * 商品 Excel 导入：解析并新增商品（已存在的编码/同名同规格会作为错误行提示）。
     */
    @PostMapping("/import")
    public Result importFromExcel(@RequestParam("file") MultipartFile file,
                                  @RequestParam(defaultValue = "false") boolean skipErrorRows) {
        return Result.success(productImportService.importProducts(file, skipErrorRows));
    }

    /** 统一的文件下载响应（文件名按 RFC 5987 编码，兼容中文名） */
    static ResponseEntity<byte[]> download(byte[] data, String fileName) {
        return FileResponses.download(data, fileName);
    }

    @DeleteMapping("/{id}")
    public Result removeById(@PathVariable Long id) {
        if (productService.getById(id) == null) {
            return Result.error("商品不存在");
        }
        long used = purchaseOrderDetailService.count(new QueryWrapper<PurchaseOrderDetail>().eq("product_id", id))
                + saleOrderDetailService.count(new QueryWrapper<SaleOrderDetail>().eq("product_id", id))
                + inventoryLogService.count(new QueryWrapper<InventoryLog>().eq("product_id", id));
        if (used > 0) {
            return Result.error("该商品已被采购/销售单据或库存流水引用，无法删除");
        }
        productService.removeById(id);
        return Result.success();
    }
}

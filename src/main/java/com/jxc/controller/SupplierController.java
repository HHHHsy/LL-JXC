package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jxc.common.PageResult;
import com.jxc.common.Result;
import com.jxc.entity.PurchaseOrder;
import com.jxc.entity.Supplier;
import com.jxc.service.PurchaseOrderService;
import com.jxc.service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/supplier")
public class SupplierController {

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    /** 分页查询 */
    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String code,
                       @RequestParam(required = false) String name,
                       @RequestParam(defaultValue = "1") long pageNum,
                       @RequestParam(defaultValue = "10") long pageSize) {
        Page<Supplier> page = supplierService.page(new Page<>(pageNum, pageSize), buildWrapper(code, name));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /** 不分页：供下拉选择使用 */
    @GetMapping("/all")
    public Result all() {
        return Result.success(supplierService.list(buildWrapper(null, null)));
    }

    private QueryWrapper<Supplier> buildWrapper(String code, String name) {
        QueryWrapper<Supplier> wrapper = new QueryWrapper<>();
        if (code != null && !code.isEmpty()) {
            wrapper.like("code", code);
        }
        if (name != null && !name.isEmpty()) {
            wrapper.like("name", name);
        }
        wrapper.orderByDesc("id");
        return wrapper;
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        Supplier supplier = supplierService.getById(id);
        if (supplier == null) {
            return Result.error("供应商不存在");
        }
        return Result.success(supplier);
    }

    @PostMapping
    public Result save(@RequestBody Supplier supplier) {
        if (supplier.getCode() == null || supplier.getCode().trim().isEmpty()) {
            throw new RuntimeException("请输入供应商编码");
        }
        if (supplier.getName() == null || supplier.getName().trim().isEmpty()) {
            throw new RuntimeException("请输入供应商名称");
        }
        supplier.setId(null);
        supplierService.save(supplier);
        return Result.success();
    }

    @PutMapping
    public Result updateById(@RequestBody Supplier supplier) {
        if (supplier.getId() == null) {
            throw new RuntimeException("缺少供应商ID");
        }
        if (supplierService.getById(supplier.getId()) == null) {
            throw new RuntimeException("供应商不存在");
        }
        supplierService.updateById(supplier);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result removeById(@PathVariable Long id) {
        if (supplierService.getById(id) == null) {
            return Result.error("供应商不存在");
        }
        if (purchaseOrderService.count(new QueryWrapper<PurchaseOrder>().eq("supplier_id", id)) > 0) {
            return Result.error("该供应商已存在采购单据，无法删除");
        }
        supplierService.removeById(id);
        return Result.success();
    }
}

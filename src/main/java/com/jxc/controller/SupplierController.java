package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.Result;
import com.jxc.entity.Supplier;
import com.jxc.service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/supplier")
public class SupplierController {

    @Autowired
    private SupplierService supplierService;

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String code,
                       @RequestParam(required = false) String name) {
        QueryWrapper<Supplier> wrapper = new QueryWrapper<>();
        if (code != null && !code.isEmpty()) {
            wrapper.eq("code", code);
        }
        if (name != null && !name.isEmpty()) {
            wrapper.like("name", name);
        }
        return Result.success(supplierService.list(wrapper));
    }

    @GetMapping("/all")
    public Result all() {
        return Result.success(supplierService.list());
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        return Result.success(supplierService.getById(id));
    }

    @PostMapping
    public Result save(@RequestBody Supplier supplier) {
        try {
            supplierService.save(supplier);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    public Result updateById(@RequestBody Supplier supplier) {
        try {
            supplierService.updateById(supplier);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result removeById(@PathVariable Long id) {
        supplierService.removeById(id);
        return Result.success();
    }
}

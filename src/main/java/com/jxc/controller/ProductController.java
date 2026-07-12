package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.Result;
import com.jxc.entity.Product;
import com.jxc.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String code,
                       @RequestParam(required = false) String name,
                       @RequestParam(required = false) String category) {
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        if (code != null && !code.isEmpty()) {
            wrapper.eq("code", code);
        }
        if (name != null && !name.isEmpty()) {
            wrapper.like("name", name);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.like("category", category);
        }
        return Result.success(productService.list(wrapper));
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    @PostMapping
    public Result save(@RequestBody Product product) {
        try {
            productService.save(product);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    public Result updateById(@RequestBody Product product) {
        try {
            productService.updateById(product);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result removeById(@PathVariable Long id) {
        productService.removeById(id);
        return Result.success();
    }
}

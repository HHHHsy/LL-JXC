package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.Result;
import com.jxc.entity.Customer;
import com.jxc.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String code,
                       @RequestParam(required = false) String name) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        if (code != null && !code.isEmpty()) {
            wrapper.eq("code", code);
        }
        if (name != null && !name.isEmpty()) {
            wrapper.like("name", name);
        }
        return Result.success(customerService.list(wrapper));
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        return Result.success(customerService.getById(id));
    }

    @PostMapping
    public Result save(@RequestBody Customer customer) {
        try {
            customerService.save(customer);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    public Result updateById(@RequestBody Customer customer) {
        try {
            customerService.updateById(customer);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result removeById(@PathVariable Long id) {
        customerService.removeById(id);
        return Result.success();
    }
}

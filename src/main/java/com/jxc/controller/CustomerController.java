package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jxc.common.PageResult;
import com.jxc.common.Result;
import com.jxc.entity.Customer;
import com.jxc.entity.SaleOrder;
import com.jxc.service.CustomerService;
import com.jxc.service.SaleOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private SaleOrderService saleOrderService;

    /** 分页查询 */
    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String code,
                       @RequestParam(required = false) String name,
                       @RequestParam(defaultValue = "1") long pageNum,
                       @RequestParam(defaultValue = "10") long pageSize) {
        Page<Customer> page = customerService.page(new Page<>(pageNum, pageSize), buildWrapper(code, name));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /** 不分页：供下拉选择使用 */
    @GetMapping("/all")
    public Result all() {
        return Result.success(customerService.list(buildWrapper(null, null)));
    }

    private QueryWrapper<Customer> buildWrapper(String code, String name) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
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
        Customer customer = customerService.getById(id);
        if (customer == null) {
            return Result.error("客户不存在");
        }
        return Result.success(customer);
    }

    @PostMapping
    public Result save(@RequestBody Customer customer) {
        if (customer.getCode() == null || customer.getCode().trim().isEmpty()) {
            throw new RuntimeException("请输入客户编码");
        }
        if (customer.getName() == null || customer.getName().trim().isEmpty()) {
            throw new RuntimeException("请输入客户名称");
        }
        customer.setId(null);
        customerService.save(customer);
        return Result.success();
    }

    @PutMapping
    public Result updateById(@RequestBody Customer customer) {
        if (customer.getId() == null) {
            throw new RuntimeException("缺少客户ID");
        }
        if (customerService.getById(customer.getId()) == null) {
            throw new RuntimeException("客户不存在");
        }
        customerService.updateById(customer);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result removeById(@PathVariable Long id) {
        if (customerService.getById(id) == null) {
            return Result.error("客户不存在");
        }
        if (saleOrderService.count(new QueryWrapper<SaleOrder>().eq("customer_id", id)) > 0) {
            return Result.error("该客户已存在销售单据，无法删除");
        }
        customerService.removeById(id);
        return Result.success();
    }
}

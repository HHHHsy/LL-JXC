package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxc.entity.Customer;
import com.jxc.mapper.CustomerMapper;
import com.jxc.service.CustomerService;
import org.springframework.stereotype.Service;

@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {

    @Override
    public boolean save(Customer entity) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("code", entity.getCode());
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("编码已存在");
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(Customer entity) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("code", entity.getCode());
        wrapper.ne("id", entity.getId());
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("编码已存在");
        }
        return super.updateById(entity);
    }
}

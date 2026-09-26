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
        checkCodeUnique(entity.getCode(), null);
        return super.save(entity);
    }

    @Override
    public boolean updateById(Customer entity) {
        checkCodeUnique(entity.getCode(), entity.getId());
        return super.updateById(entity);
    }

    private void checkCodeUnique(String code, Long excludeId) {
        if (code == null || code.trim().isEmpty()) {
            return;
        }
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("code", code);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("客户编码已存在");
        }
    }
}

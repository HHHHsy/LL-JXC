package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxc.entity.Supplier;
import com.jxc.mapper.SupplierMapper;
import com.jxc.service.SupplierService;
import org.springframework.stereotype.Service;

@Service
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {

    @Override
    public boolean save(Supplier entity) {
        QueryWrapper<Supplier> wrapper = new QueryWrapper<>();
        wrapper.eq("code", entity.getCode());
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("编码已存在");
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(Supplier entity) {
        QueryWrapper<Supplier> wrapper = new QueryWrapper<>();
        wrapper.eq("code", entity.getCode());
        wrapper.ne("id", entity.getId());
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("编码已存在");
        }
        return super.updateById(entity);
    }
}

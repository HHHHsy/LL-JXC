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
        checkCodeUnique(entity.getCode(), null);
        return super.save(entity);
    }

    @Override
    public boolean updateById(Supplier entity) {
        checkCodeUnique(entity.getCode(), entity.getId());
        return super.updateById(entity);
    }

    private void checkCodeUnique(String code, Long excludeId) {
        if (code == null || code.trim().isEmpty()) {
            return;
        }
        QueryWrapper<Supplier> wrapper = new QueryWrapper<>();
        wrapper.eq("code", code);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("供应商编码已存在");
        }
    }
}

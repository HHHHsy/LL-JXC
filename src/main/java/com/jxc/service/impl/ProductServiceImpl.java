package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxc.entity.Product;
import com.jxc.mapper.ProductMapper;
import com.jxc.service.ProductService;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Override
    public boolean save(Product entity) {
        checkCodeUnique(entity.getCode(), null);
        return super.save(entity);
    }

    @Override
    public boolean updateById(Product entity) {
        checkCodeUnique(entity.getCode(), entity.getId());
        return super.updateById(entity);
    }

    private void checkCodeUnique(String code, Long excludeId) {
        if (code == null || code.trim().isEmpty()) {
            return;
        }
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        wrapper.eq("code", code);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("商品编码已存在");
        }
    }
}

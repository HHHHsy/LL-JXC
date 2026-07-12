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
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        wrapper.eq("code", entity.getCode());
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("编码已存在");
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(Product entity) {
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        wrapper.eq("code", entity.getCode());
        wrapper.ne("id", entity.getId());
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("编码已存在");
        }
        return super.updateById(entity);
    }
}

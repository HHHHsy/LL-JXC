package com.jxc.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxc.entity.SaleOrderDetail;
import com.jxc.mapper.SaleOrderDetailMapper;
import com.jxc.service.SaleOrderDetailService;
import org.springframework.stereotype.Service;

@Service
public class SaleOrderDetailServiceImpl extends ServiceImpl<SaleOrderDetailMapper, SaleOrderDetail> implements SaleOrderDetailService {
}

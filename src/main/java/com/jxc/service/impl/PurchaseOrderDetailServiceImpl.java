package com.jxc.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxc.entity.PurchaseOrderDetail;
import com.jxc.mapper.PurchaseOrderDetailMapper;
import com.jxc.service.PurchaseOrderDetailService;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderDetailServiceImpl extends ServiceImpl<PurchaseOrderDetailMapper, PurchaseOrderDetail> implements PurchaseOrderDetailService {
}

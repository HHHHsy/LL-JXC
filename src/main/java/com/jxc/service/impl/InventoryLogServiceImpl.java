package com.jxc.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxc.entity.InventoryLog;
import com.jxc.mapper.InventoryLogMapper;
import com.jxc.service.InventoryLogService;
import org.springframework.stereotype.Service;

@Service
public class InventoryLogServiceImpl extends ServiceImpl<InventoryLogMapper, InventoryLog> implements InventoryLogService {
}

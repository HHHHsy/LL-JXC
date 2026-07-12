package com.jxc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jxc.common.Result;
import com.jxc.entity.InventoryCheck;
import com.jxc.entity.InventoryCheckDetail;

import java.util.List;

public interface InventoryCheckService extends IService<InventoryCheck> {

    Result createCheck(InventoryCheck check, List<InventoryCheckDetail> details);
}

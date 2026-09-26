package com.jxc.controller;

import com.jxc.common.Result;
import com.jxc.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统设置（界面可维护的配置项）。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final String GROUP_DELIVERY_NOTE = "delivery-note";
    private static final String GROUP_PRINT = "print";

    @Autowired
    private ConfigService configService;

    /** 送货单设置（未在界面配置过的项返回配置文件里的默认值） */
    @GetMapping("/delivery-note")
    public Result getDeliveryNote() {
        return Result.success(configService.getGroup(GROUP_DELIVERY_NOTE));
    }

    /** 保存送货单设置 */
    @PutMapping("/delivery-note")
    public Result saveDeliveryNote(@RequestBody Map<String, String> body) {
        configService.saveGroup(GROUP_DELIVERY_NOTE, body);
        return Result.success();
    }

    /** 打印设置（打印机 / 纸张 / 份数 / 字体字号） */
    @GetMapping("/print")
    public Result getPrint() {
        return Result.success(configService.getGroup(GROUP_PRINT));
    }

    /** 保存打印设置 */
    @PutMapping("/print")
    public Result savePrint(@RequestBody Map<String, String> body) {
        configService.saveGroup(GROUP_PRINT, body);
        return Result.success();
    }
}

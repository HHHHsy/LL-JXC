package com.jxc.controller;

import com.jxc.common.Result;
import com.jxc.service.DeliveryNotePrintService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 送货单直连打印（后端渲染 → 直接送打印机，无浏览器弹窗）。
 */
@RestController
@RequestMapping("/api/print")
public class PrintController {

    @Autowired
    private DeliveryNotePrintService deliveryNotePrintService;

    /** 可用打印机、纸张选项与当前打印配置 */
    @GetMapping("/printers")
    public Result printers() {
        return Result.success(deliveryNotePrintService.printerInfo());
    }

    /** 直接打印指定销售订单的送货单 */
    @PostMapping("/delivery-note/{orderId}")
    public Result printDeliveryNote(@PathVariable Long orderId,
                                    @RequestParam(required = false) String printer,
                                    @RequestParam(required = false) String paper,
                                    @RequestParam(required = false) Integer copies) {
        return Result.success(deliveryNotePrintService.printDeliveryNote(orderId, printer, paper, copies));
    }

    /** 打印标定测试页（每 10mm 刻度网格，用于实测打印机原点偏移） */
    @PostMapping("/test-page")
    public Result printTestPage(@RequestParam(required = false) String printer) {
        return Result.success(deliveryNotePrintService.printTestPage(printer));
    }

    /** 调试：导出发往打印机的光栅图 PNG（type=note|test） */
    @GetMapping("/debug-raster")
    public ResponseEntity<byte[]> debugRaster(@RequestParam String type,
                                              @RequestParam(required = false) Long orderId) throws Exception {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .body(deliveryNotePrintService.debugRaster(type, orderId));
    }
}

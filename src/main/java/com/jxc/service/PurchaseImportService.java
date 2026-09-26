package com.jxc.service;

import com.jxc.dto.PurchaseImportPreview;
import com.jxc.dto.PurchaseImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * 采购清单（Excel）导入。
 */
public interface PurchaseImportService {

    /**
     * 仅解析并校验，不写库，用于导入前的预览。
     *
     * @param autoCreateProduct 未匹配到商品时是否将自动建档
     */
    PurchaseImportPreview parse(MultipartFile file, boolean autoCreateProduct);

    /**
     * 解析并生成一张采购订单（明细来自清单）。
     *
     * @param supplierId        供应商
     * @param purchaseDate      采购日期，为空取当天
     * @param autoCreateProduct 未匹配到商品时是否自动建档
     * @param skipErrorRows     是否忽略有问题的行，仅导入可用行
     */
    PurchaseImportResult importOrder(MultipartFile file, Long supplierId, LocalDate purchaseDate,
                                     boolean autoCreateProduct, boolean skipErrorRows);
}

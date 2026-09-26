package com.jxc.service;

import com.jxc.dto.ProductImportPreview;
import com.jxc.dto.ProductImportResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 商品 Excel 导入。
 */
public interface ProductImportService {

    /** 仅解析并校验，不写库 */
    ProductImportPreview parse(MultipartFile file);

    /**
     * 解析并新增商品。
     *
     * @param skipErrorRows 是否忽略有问题的行，仅导入可用行
     */
    ProductImportResult importProducts(MultipartFile file, boolean skipErrorRows);
}

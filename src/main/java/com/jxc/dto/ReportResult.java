package com.jxc.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 报表返回体：分页数据 + 全量合计。
 */
@Data
public class ReportResult<T> {

    private List<T> list = new ArrayList<>();

    /** 满足条件的记录总数（用于分页） */
    private long total;

    private long pageNum;

    private long pageSize;

    /** 全部查询结果的合计数量（不受分页影响） */
    private BigDecimal totalQuantity = BigDecimal.ZERO;

    /** 全部查询结果的合计金额（不受分页影响） */
    private BigDecimal totalAmount = BigDecimal.ZERO;
}

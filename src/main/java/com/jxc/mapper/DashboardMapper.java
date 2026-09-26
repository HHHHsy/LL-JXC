package com.jxc.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 首页看板统计 Mapper（SQL 见 resources/mapper/DashboardMapper.xml）。
 */
public interface DashboardMapper {

    /** 近 N 个月已入库采购金额：month(yyyy-MM) + amount */
    List<Map<String, Object>> purchaseTrend(@Param("fromMonth") String fromMonth);

    /** 近 N 个月已出库销售金额：month(yyyy-MM) + amount */
    List<Map<String, Object>> saleTrend(@Param("fromMonth") String fromMonth);

    /** 商品分类分布：name + value */
    List<Map<String, Object>> categoryDistribution();

    /** 最近出入库流水：changeTime/changeType/changeQuantity/relatedOrderNo/remark/productName */
    List<Map<String, Object>> recentLogs(@Param("limit") int limit);

    /** 指定月份热销 Top N：productName + quantity */
    List<Map<String, Object>> topSaleProducts(@Param("month") String month, @Param("limit") int limit);

    /** 低库存商品数 */
    long countLowStock(@Param("threshold") int threshold);
}

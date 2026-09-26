package com.jxc.mapper;

import com.jxc.dto.PurchaseReportRow;
import com.jxc.dto.SaleReportRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 报表查询 Mapper（SQL 见 resources/mapper/ReportMapper.xml）。
 *
 * <p>聚合与关联全部下推到数据库执行，避免把整表数据捞到内存里再计算。
 * {@code status} 为可选的订单状态过滤（不传=全部，已取消始终排除）。</p>
 */
public interface ReportMapper {

    List<PurchaseReportRow> selectPurchaseDetail(@Param("startDate") String startDate,
                                                 @Param("endDate") String endDate,
                                                 @Param("supplierId") Long supplierId,
                                                 @Param("status") String status,
                                                 @Param("offset") long offset,
                                                 @Param("limit") long limit);

    long countPurchaseDetail(@Param("startDate") String startDate,
                             @Param("endDate") String endDate,
                             @Param("supplierId") Long supplierId,
                             @Param("status") String status);

    Map<String, Object> sumPurchaseDetail(@Param("startDate") String startDate,
                                          @Param("endDate") String endDate,
                                          @Param("supplierId") Long supplierId,
                                          @Param("status") String status);

    List<SaleReportRow> selectSaleDetail(@Param("startDate") String startDate,
                                         @Param("endDate") String endDate,
                                         @Param("customerId") Long customerId,
                                         @Param("status") String status,
                                         @Param("offset") long offset,
                                         @Param("limit") long limit);

    long countSaleDetail(@Param("startDate") String startDate,
                         @Param("endDate") String endDate,
                         @Param("customerId") Long customerId,
                         @Param("status") String status);

    Map<String, Object> sumSaleDetail(@Param("startDate") String startDate,
                                      @Param("endDate") String endDate,
                                      @Param("customerId") Long customerId,
                                      @Param("status") String status);
}

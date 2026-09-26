package com.jxc.service;

import java.util.Map;

/**
 * 首页看板统计。
 */
public interface DashboardService {

    /** 看板所需的全部统计数据 */
    Map<String, Object> stats();
}

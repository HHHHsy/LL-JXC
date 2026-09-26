package com.jxc.service;

import java.util.Map;

/**
 * 系统配置读写（键值对，缺省值来自 application.yml，界面保存的值优先）。
 */
public interface ConfigService {

    /** 读取配置组（如 delivery-note），返回「键去掉前缀」后的值与缺省值合并结果 */
    Map<String, String> getGroup(String group);

    /** 保存配置组（键会自动加上前缀），已存在则覆盖 */
    void saveGroup(String group, Map<String, String> values);

    /** 取单个配置（DB 优先，其次 yml 缺省） */
    String get(String group, String key, String defaultValue);

    /** 取整数配置 */
    int getInt(String group, String key, int defaultValue);
}

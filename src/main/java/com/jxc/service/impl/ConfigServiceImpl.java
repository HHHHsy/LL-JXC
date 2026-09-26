package com.jxc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.entity.SysConfig;
import com.jxc.mapper.SysConfigMapper;
import com.jxc.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统配置实现：以 sys_config 表为准，**只对从未保存过的配置项**使用 application.yml 里的默认值。
 *
 * <p>语义：客户在界面保存过的值（包括清空成空白）优先，做到「所见即所印」；
 * 老部署或全新安装没保存过任何配置时，自动使用配置文件里的默认抬头，不会出现空白单据。</p>
 */
@Service
public class ConfigServiceImpl implements ConfigService {

    private static final String PREFIX = "config.";

    @Autowired
    private SysConfigMapper sysConfigMapper;

    /* 送货单缺省值（与 application.yml 的 jxc.delivery-note.* 一致） */
    @Value("${jxc.delivery-note.company-name:}")
    private String defaultCompanyName;

    @Value("${jxc.delivery-note.title:送货单}")
    private String defaultTitle;

    @Value("${jxc.delivery-note.delivery-man:}")
    private String defaultDeliveryMan;

    @Value("${jxc.delivery-note.service-phone:}")
    private String defaultServicePhone;

    @Value("${jxc.delivery-note.service-address:}")
    private String defaultServiceAddress;

    @Value("${jxc.delivery-note.footer-note:}")
    private String defaultFooterNote;

    @Value("${jxc.delivery-note.min-rows:4}")
    private int defaultMinRows;

    /* 打印缺省值（与 application.yml 的 jxc.print.* 一致） */
    @Value("${jxc.print.printer-name:}")
    private String defaultPrinterName;

    @Value("${jxc.print.paper:dot-241-140}")
    private String defaultPaper;

    @Value("${jxc.print.copies:1}")
    private String defaultCopies;

    @Value("${jxc.print.font:宋体}")
    private String defaultFont;

    @Value("${jxc.print.font-size:9}")
    private String defaultFontSize;

    @Override
    public Map<String, String> getGroup(String group) {
        Map<String, String> result = new LinkedHashMap<>(defaults(group));
        String prefix = PREFIX + group + ".";
        QueryWrapper<SysConfig> wrapper = new QueryWrapper<>();
        wrapper.likeRight("config_key", prefix);
        for (SysConfig config : sysConfigMapper.selectList(wrapper)) {
            String key = config.getConfigKey().substring(prefix.length());
            // 只要在界面保存过（哪怕保存成空），就以数据库里的值为准：
            // 「所见即所印」，清空某项就真的打印空白；只有从未保存过的项才用配置文件默认值。
            result.put(key, config.getConfigValue() == null ? "" : config.getConfigValue());
        }
        return result;
    }

    @Override
    @Transactional
    public void saveGroup(String group, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()) {
                continue;
            }
            String key = PREFIX + group + "." + entry.getKey();
            SysConfig config = sysConfigMapper.selectById(key);
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (config == null) {
                config = new SysConfig();
                config.setConfigKey(key);
                config.setConfigValue(value);
                config.setRemark(group);
                sysConfigMapper.insert(config);
            } else {
                config.setConfigValue(value);
                sysConfigMapper.updateById(config);
            }
        }
    }

    @Override
    public String get(String group, String key, String defaultValue) {
        SysConfig config = sysConfigMapper.selectById(PREFIX + group + "." + key);
        // 未保存过 → 用默认值；保存过（含空）→ 以库里的值为准
        return config == null ? defaultValue : (config.getConfigValue() == null ? "" : config.getConfigValue());
    }

    @Override
    public int getInt(String group, String key, int defaultValue) {
        String value = get(group, key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 各配置组的缺省值（来自 application.yml） */
    private Map<String, String> defaults(String group) {
        Map<String, String> map = new LinkedHashMap<>();
        if ("delivery-note".equals(group)) {
            map.put("companyName", defaultCompanyName);
            map.put("title", defaultTitle);
            map.put("deliveryMan", defaultDeliveryMan);
            map.put("servicePhone", defaultServicePhone);
            map.put("serviceAddress", defaultServiceAddress);
            map.put("footerNote", defaultFooterNote);
            map.put("minRows", String.valueOf(defaultMinRows));
        } else if ("print".equals(group)) {
            map.put("printerName", defaultPrinterName);
            map.put("paper", defaultPaper);
            map.put("copies", defaultCopies);
            map.put("font", defaultFont);
            map.put("fontSize", defaultFontSize);
        }
        return map;
    }
}

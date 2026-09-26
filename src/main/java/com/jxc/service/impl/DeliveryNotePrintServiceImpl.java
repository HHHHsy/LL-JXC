package com.jxc.service.impl;

import com.jxc.common.DeliveryNoteRenderer;
import com.jxc.common.PaperPresets;
import com.jxc.common.PrinterSupport;
import com.jxc.dto.DeliveryNote;
import com.jxc.service.ConfigService;
import com.jxc.service.DeliveryNotePrintService;
import com.jxc.service.DeliveryNoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.print.PrintService;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 送货单直连打印：用 Java2D 渲染同一版式，通过 javax.print 直接送到打印机，全程无弹窗。
 */
@Service
public class DeliveryNotePrintServiceImpl implements DeliveryNotePrintService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryNotePrintServiceImpl.class);
    private static final String GROUP = "print";

    @Autowired
    private DeliveryNoteService deliveryNoteService;

    @Autowired
    private ConfigService configService;

    /** 单次送印的最长等待秒数：打印机卡死时不至于让请求一直挂着 */
    @Value("${jxc.print.timeout-seconds:20}")
    private int printTimeoutSeconds;

    @Override
    public Map<String, Object> printerInfo() {
        List<PrinterSupport.Info> printers = PrinterSupport.list();
        String defaultName = "";
        List<Map<String, Object>> items = new ArrayList<>();
        for (PrinterSupport.Info info : printers) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", info.name());
            item.put("default", info.isDefault());
            item.put("virtual", info.virtual());
            item.put("model", info.model());
            items.add(item);
            if (info.isDefault()) {
                defaultName = info.name();
            }
        }

        Map<String, String> config = configService.getGroup(GROUP);
        Readiness readiness = checkReadiness(firstNonBlank(config.get("printerName")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("printers", items);
        result.put("defaultPrinter", defaultName);
        result.put("papers", PaperPresets.options());
        result.put("config", config);
        // 直连打印是否可用：前端据此禁用「直接打印」并给出原因，避免点了之后只等到一个超时
        result.put("effectivePrinter", readiness.printerName());
        result.put("directPrintReady", readiness.ready());
        result.put("directPrintReason", readiness.reason());
        result.put("directPrintHint", readiness.hint());
        return result;
    }

    @Override
    public Map<String, Object> printDeliveryNote(Long orderId, String printerName, String paperKey, Integer copies) {
        Map<String, String> config = configService.getGroup(GROUP);
        String targetPrinter = firstNonBlank(printerName, config.get("printerName"));
        String paper = PaperPresets.isValid(paperKey) ? paperKey
                : (PaperPresets.isValid(config.get("paper")) ? config.get("paper") : PaperPresets.DEFAULT_KEY);
        int copiesValue = copies != null && copies > 0 ? copies : parseInt(config.get("copies"), 1);
        String fontFamily = firstNonBlank(config.get("font"), "宋体");
        int fontSize = parseInt(config.get("fontSize"), 9);

        // 先取单据数据：订单不存在这类问题要优先报出来，而不是先抱怨打印机
        DeliveryNote note = deliveryNoteService.buildForSaleOrder(orderId);

        // 送印前再判断"这台打印机到底能不能出纸"：没打印机/名字对不上/选到虚拟 PDF 打印机
        // 都要立刻给出明确提示，而不是让 job.print() 卡在那里等前端超时。
        Readiness readiness = checkReadiness(targetPrinter);
        if (!readiness.ready()) {
            log.warn("直连打印被拦下：{}（目标打印机：{}）", readiness.reason(), targetPrinter);
            throw new RuntimeException("无法直接打印：" + readiness.reason() + "。" + readiness.hint());
        }
        PrintService printService = PrinterSupport.resolve(readiness.printerName());
        if (printService == null) {
            throw new RuntimeException("无法直接打印：打印机「" + readiness.printerName()
                    + "」当前不可用（可能已删除或驱动异常），请在「系统设置 → 打印设置」重新选择");
        }

        PaperPresets.Preset preset = PaperPresets.of(paper);

        DeliveryNoteRenderer renderer = new DeliveryNoteRenderer(note, preset, fontFamily, fontSize);
        PrinterJob job = PrinterJob.getPrinterJob();
        try {
            job.setPrintService(printService);
            job.setPrintable(renderer, DeliveryNoteRenderer.buildPageFormat(preset));
            job.setJobName("送货单 " + note.getOrderNo());
            // 份数：多数打印机支持 job.setCopies；个别驱动会把它限制为 1，
            // 这时退化为重复提交打印任务，避免"设了 2 份只出一份"。
            int repeat = 1;
            if (copiesValue > 1) {
                job.setCopies(copiesValue);
                int accepted = job.getCopies();
                if (accepted != copiesValue) {
                    log.warn("打印机 {} 只接受 {} 份，改为重复提交 {} 次", printService.getName(), accepted, copiesValue);
                    job.setCopies(1);
                    repeat = copiesValue;
                }
            }
            for (int i = 0; i < repeat; i++) {
                PrinterSupport.runWithTimeout(job, printTimeoutSeconds, printService.getName());
            }
        } catch (PrinterException e) {
            log.error("打印送货单失败: {}", e.getMessage(), e);
            throw new RuntimeException("打印失败：" + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", note.getOrderNo());
        result.put("printer", printService.getName());
        result.put("paper", preset.label());
        result.put("copies", copiesValue);
        result.put("itemCount", note.getItems().size());
        result.put("pages", renderer.estimatePages());
        result.put("totalAmount", note.getTotalAmount());
        log.info("送货单已送打印机：单号={} 打印机={} 纸张={} 份数={} 预计页数={}",
                note.getOrderNo(), printService.getName(), preset.label(), copiesValue, renderer.estimatePages());
        return result;
    }

    /**
     * 判断目标打印机能否真正出纸。
     *
     * <p>四种情况都要单独说清楚：没有打印机、没设置也没默认、名字对不上、选中了虚拟打印机。</p>
     */
    private Readiness checkReadiness(String targetPrinter) {
        List<PrinterSupport.Info> printers = PrinterSupport.list();
        if (printers.isEmpty()) {
            return new Readiness("", "", false, "本机未检测到任何打印机",
                    "请先在运行本软件的电脑上安装打印机驱动（针式机建议在驱动里新建 241×140mm 纸张），"
                            + "或改用「浏览器打印」在连接了打印机的电脑上打印。");
        }
        String available = PrinterSupport.joinNames(printers);

        // 没在设置里指定打印机时，用 Windows 默认打印机（与老版本行为一致）
        PrinterSupport.Info info = PrinterSupport.find(printers, targetPrinter);
        if (info == null && (targetPrinter == null || targetPrinter.isBlank())) {
            for (PrinterSupport.Info candidate : printers) {
                if (candidate.isDefault()) {
                    info = candidate;
                    break;
                }
            }
        }
        if (info == null) {
            if (targetPrinter == null || targetPrinter.isBlank()) {
                return new Readiness("", "", false, "还没有选择打印机，且 Windows 没有默认打印机",
                        "请在「系统设置 → 打印设置」里选择打印机。本机可用打印机：" + available + "。");
            }
            return new Readiness(targetPrinter, "", false, "未找到打印机「" + targetPrinter + "」",
                    "它可能已改名、被删除或尚未连接，请在「系统设置 → 打印设置」重新选择。本机当前可用打印机："
                            + available + "。");
        }
        if (info.virtual()) {
            String driver = info.model().isEmpty() ? "" : "（驱动：" + info.model() + "）";
            return new Readiness(info.name(), info.model(), false,
                    "「" + info.name() + "」" + driver + "属于「打印到文件」类的虚拟打印机，不会真的出纸",
                    "它会让系统弹出「另存为」窗口并一直等待，所以不能用「直接打印」。"
                            + "请改选真实的针式/激光打印机；若只是想导出 PDF 留档，请用「浏览器打印」并在打印对话框里选择"
                            + "「另存为 PDF」。");
        }
        return new Readiness(info.name(), info.model(), true, "", "");
    }

    /** 直连打印就绪情况：ready=false 时 reason 说明原因、hint 给出处理办法 */
    private record Readiness(String printerName, String model, boolean ready, String reason, String hint) {
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

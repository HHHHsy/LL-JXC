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

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.PrinterResolution;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        // 纸张表单匹配检测：驱动默认表单小于单据纸时，打印必然错位/截断，提前提示
        result.put("paperFormHint", paperFormHint(
                PrinterSupport.resolve(readiness.printerName()),
                PaperPresets.of(PaperPresets.isValid(config.get("paper")) ? config.get("paper") : PaperPresets.DEFAULT_KEY)));
        return result;
    }

    /**
     * 检测驱动默认纸张表单是否装得下单据纸：
     * 针式打印机默认表单若是 Letter/A4（可打印宽约 203mm），241mm 宽的单据会被驱动
     * 按错位映射，表现为左侧/顶部被裁、内容偏移——任何软件侧校准都救不回来，
     * 必须在 Windows 里新建 241×140mm 表单并设为该打印机默认纸张。
     */
    private String paperFormHint(PrintService service, PaperPresets.Preset preset) {
        if (service == null) {
            return "";
        }
        try {
            Object value = service.getDefaultAttributeValue(MediaPrintableArea.class);
            if (value instanceof MediaPrintableArea area) {
                float[] a = area.getPrintableArea(MediaPrintableArea.MM);
                if (a[2] < preset.widthMm() - 10) {
                    return String.format(
                            "打印机「%s」当前默认表单可打印宽度约 %.0fmm，小于单据纸 %.0f×%.0fmm——"
                                    + "请在 Windows「设置→打印机和扫描仪→打印机属性」或旧版「打印服务器属性→创建新格式」"
                                    + "新建 %.0f×%.0fmm 的表单，并在打印机首选项里把默认纸张改为它，然后重启本系统。",
                            service.getName(), a[2], preset.widthMm(), preset.heightMm(),
                            preset.widthMm(), preset.heightMm());
                }
            }
        } catch (Exception ignored) {
            // 读不到不影响主流程
        }
        return "";
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
        // 偏移校准（毫米，向右/向下为正）：做进"可打印区"本身，见 offsetPageFormat 的说明
        double offXmm = parseDouble(config.get("offsetXmm"));
        double offYmm = parseDouble(config.get("offsetYmm"));
        PageFormat pageFormat = offsetPageFormat(preset, offXmm, offYmm);

        DeliveryNoteRenderer renderer = new DeliveryNoteRenderer(note, preset, fontFamily, fontSize);
        PrinterJob job = PrinterJob.getPrinterJob();
        try {
            job.setPrintService(printService);
            job.setPrintable(anchored(renderer), pageFormat);
            job.setJobName("送货单 " + note.getOrderNo());
            log.info("送货单落纸：单号={} 纸张={} 可打印区原点=({}mm, {}mm)（已含偏移 {}mm / {}mm）",
                    note.getOrderNo(), preset.label(),
                    round1(pageFormat.getImageableX() / PaperPresets.MM_TO_PT),
                    round1(pageFormat.getImageableY() / PaperPresets.MM_TO_PT), offXmm, offYmm);
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
            HashPrintRequestAttributeSet attributes = printAttributes(printService);
            for (int i = 0; i < repeat; i++) {
                PrinterSupport.runWithTimeout(job, attributes, printTimeoutSeconds, printService.getName());
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
     * 送印属性：把打印分辨率钉在驱动自身支持的最高水平分辨率上。
     *
     * <p>为什么必须显式指定：本机 Epson LQ-610KII 驱动在默认的 180×180 模式下，会按 180dpi 光栅化内容、
     * 却用 360dpi 的点阵指令（{@code ESC * m=39}）输出，整页被印成一半大小——实测 227mm 的参照框
     * 只印出 113.5mm，表现为"纸右边一大片空白、字偏小看不清"。
     * 显式请求驱动自己列出的 360×180 之后，点阵列数与声明 dpi 一致（3216 列 × 360dpi = 226.9mm），
     * 输出即为 1:1（用"打印到文件"解析点阵流实测确认）。</p>
     */
    private HashPrintRequestAttributeSet printAttributes(PrintService service) {
        HashPrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        try {
            Object value = service.getSupportedAttributeValues(PrinterResolution.class, null, null);
            PrinterResolution best = null;
            if (value instanceof PrinterResolution[] options) {
                for (PrinterResolution option : options) {
                    if (best == null
                            || option.getCrossFeedResolution(PrinterResolution.DPI)
                            > best.getCrossFeedResolution(PrinterResolution.DPI)) {
                        best = option;
                    }
                }
            }
            if (best != null) {
                attributes.add(best);
                log.info("送印分辨率：{}（不指定时该驱动会按一半大小输出）", best);
            }
        } catch (Exception e) {
            log.warn("读取打印机可用分辨率失败，按驱动默认送印：{}", e.getMessage());
        }
        return attributes;
    }

    /**
     * 把渲染器"钉"到纸张的可打印区上——直连打印不裁左、不裁上的关键一步。
     *
     * <p>Java 打印传给 {@code Printable.print()} 的坐标原点是<b>纸张物理左上角</b>
     * （OpenJDK RasterPrinterJob 原话：user (0,0) should be origin of page, not imageable area；
     * 官方示例里人人必写的 {@code g2d.translate(pf.getImageableX(), pf.getImageableY())} 就是为它），
     * 而 {@link DeliveryNoteRenderer} 的版式是按<b>可打印区左上角</b>设计的：从 (0,0) 画起、
     * 宽度取 imageableWidth。两者相差 (imageableX, imageableY)，对 241×93 纸就是左边距 8mm、上边距 5mm。</p>
     *
     * <p>不补这一步，整张单据会被顶到纸张物理左上角，落在针式打印机不可打印区里的部分
     * （左侧送纸孔区、页顶压纸区）直接被裁掉，表现为"左边少字、上边缺头"；而保留这一平移后，
     * 同一套版式与预览（{@link DeliveryNoteRenderer#renderToImage}，本身就按边距平移过）逐毫米对齐，
     * 也和 Excel/浏览器打印的落纸口径一致（都由可打印区左上角起算）。</p>
     */
    private static Printable anchored(DeliveryNoteRenderer renderer) {
        return (graphics, pageFormat, pageIndex) -> {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            try {
                return renderer.print(g, pageFormat, pageIndex);
            } finally {
                g.dispose();
            }
        };
    }

    /**
     * 按用户偏移校准生成页面格式：把偏移加在"可打印区"的起点上（并夹紧在纸张范围内）。
     *
     * <p>为什么不直接平移绘图：Java 会把绘制裁剪到页面格式声明的可打印区，平移几毫米就会把<b>对侧</b>
     * 的边框裁掉（往左挪裁左边框、往右挪裁右边框），偏移校准等于失效。
     * 把偏移做进可打印区起点，效果是"整块版心换个位置"，左右边框都保得住。</p>
     */
    private PageFormat offsetPageFormat(PaperPresets.Preset preset, double offXmm, double offYmm) {
        double maxLeft = Math.max(0, preset.widthMm() - preset.imageableWidthPt() / PaperPresets.MM_TO_PT);
        double maxTop = Math.max(0, preset.heightMm() - preset.imageableHeightPt() / PaperPresets.MM_TO_PT);
        double left = Math.min(Math.max(0, preset.marginLeftMm() + offXmm), maxLeft);
        double top = Math.min(Math.max(0, preset.marginTopMm() + offYmm), maxTop);
        java.awt.print.Paper paper = new java.awt.print.Paper();
        paper.setSize(preset.widthPt(), preset.heightPt());
        paper.setImageableArea(left * PaperPresets.MM_TO_PT, top * PaperPresets.MM_TO_PT,
                preset.imageableWidthPt(), preset.imageableHeightPt());
        PageFormat pageFormat = new PageFormat();
        pageFormat.setPaper(paper);
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        return pageFormat;
    }

    /**
     * 标定测试页：画已知坐标的刻度网格（每 10mm），刻度自纸张左上角起算，
     * 用于实测打印机物理边距，并在需要时确定「偏移校准」该填多少。
     */
    @Override
    public Map<String, Object> printTestPage(String printerName) {
        Map<String, String> config = configService.getGroup(GROUP);
        Readiness readiness = checkReadiness(firstNonBlank(printerName, config.get("printerName")));
        if (!readiness.ready()) {
            throw new RuntimeException("无法直接打印：" + readiness.reason() + "。" + readiness.hint());
        }
        PrintService printService = PrinterSupport.resolve(readiness.printerName());
        if (printService == null) {
            throw new RuntimeException("打印机不可用");
        }

        PaperPresets.Preset preset = PaperPresets.of(PaperPresets.isValid(config.get("paper"))
                ? config.get("paper") : PaperPresets.DEFAULT_KEY);

        PrinterJob job = PrinterJob.getPrinterJob();
        try {
            job.setPrintService(printService);
            // 标定页要"量纸"：纸张设为整张纸、可打印区也设为整张纸（不让 Java 提前裁剪），
            // 于是纸上第一个能印出来的刻度就是打印机真实左边界，最后一个可见刻度就是真实右边界
            java.awt.print.Paper rulerPaper = new java.awt.print.Paper();
            rulerPaper.setSize(preset.widthPt(), preset.heightPt());
            rulerPaper.setImageableArea(0, 0, preset.widthPt(), preset.heightPt());
            PageFormat format = new PageFormat();
            format.setPaper(rulerPaper);
            format.setOrientation(PageFormat.PORTRAIT);

            final double paperW = preset.widthPt();
            final double paperH = preset.heightPt();
            job.setPrintable((g, pf, pageIndex) -> {
                if (pageIndex > 0) {
                    return Printable.NO_SUCH_PAGE;
                }
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("宋体", Font.PLAIN, 7));
                double mm = PaperPresets.MM_TO_PT;
                // 整张纸的外框 + 顶部/左侧标尺（数字 = 距纸左边缘 / 上边缘的毫米数）
                g2.drawRect(0, 0, (int) Math.round(paperW), (int) Math.round(paperH));
                for (int m = 0; m * mm <= paperW; m += 10) {
                    int x = (int) Math.round(m * mm);
                    g2.drawLine(x, 0, x, (int) Math.round((m % 50 == 0 ? 8 : 4) * mm));
                    if (m % 20 == 0) {
                        g2.drawString(String.valueOf(m), x + 1, (int) Math.round(11 * mm));
                    }
                }
                for (int m = 10; m * mm <= paperH; m += 10) {
                    int y = (int) Math.round(m * mm);
                    g2.drawLine(0, y, (int) Math.round((m % 50 == 0 ? 8 : 4) * mm), y);
                    if (m % 20 == 0) {
                        g2.drawString(String.valueOf(m), (int) Math.round(9 * mm), y + 3);
                    }
                }
                // 底部通栏黑条：能印到多长，一眼可见
                g2.fillRect(0, (int) Math.round(paperH - 5 * mm),
                        (int) Math.round(paperW), (int) Math.round(5 * mm));
                g2.setFont(new Font("宋体", Font.PLAIN, 8));
                g2.drawString("标定页：刻度 = 距纸左边缘的毫米数。能印出来的最小/最大数字就是这台机器的真实可打印范围",
                        (int) Math.round(20 * mm), (int) Math.round(28 * mm));
                return Printable.PAGE_EXISTS;
            }, format);
            PrinterSupport.runWithTimeout(job, printAttributes(printService), printTimeoutSeconds, printService.getName());
        } catch (PrinterException e) {
            log.error("打印标定页失败: {}", e.getMessage(), e);
            throw new RuntimeException("打印失败：" + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("printer", printService.getName());
        result.put("message", "标定页已发送（整张纸的尺子）：纸上第一个能看见的刻度就是打印机左边距，"
                + "最后一个可见刻度就是可打印右边界。若送货单右边被裁，把「偏移校准」水平值填负数（内容往左挪）；"
                + "左边留白过多则填正数（往右挪）。");
        return result;
    }

    // ==================== RAW ESC/P2 光栅直打（针式纸张专用） ====================

    /** 光栅分辨率：180dpi（ESC/P2 24 点带模式，点距 0.141mm） */
    private static final double RASTER_DPI = 180.0;

    /**
     * 把送货单渲染成整页光栅图并转成 ESC/P2 指令流。
     * 版心 = 纸张预设边距 + 用户偏移校准（毫米级，直接作用于光栅，无驱动参与）。
     */
    private byte[] renderNoteToEscp(DeliveryNote note, PaperPresets.Preset preset, DeliveryNoteRenderer renderer,
                                    double offXmm, double offYmm) {
        return toEscp(renderNoteToImage(note, preset, renderer, offXmm, offYmm));
    }

    private BufferedImage renderNoteToImage(DeliveryNote note, PaperPresets.Preset preset, DeliveryNoteRenderer renderer,
                                            double offXmm, double offYmm) {
        double scale = RASTER_DPI / 72.0;
        int w = (int) Math.round(preset.widthPt() * scale);
        int bands = (int) Math.floor(preset.heightPt() * scale / 24.0);
        int h = bands * 24;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.translate((preset.marginLeftMm() + offXmm) * PaperPresets.MM_TO_PT * scale,
                (preset.marginTopMm() + offYmm) * PaperPresets.MM_TO_PT * scale);
        g.scale(scale, scale);
        g.setClip(0, 0, (int) Math.round(preset.imageableWidthPt()), (int) Math.round(preset.imageableHeightPt()));
        try {
            renderer.print(g, DeliveryNoteRenderer.buildPageFormat(preset), 0);
        } catch (PrinterException e) {
            throw new IllegalStateException("渲染送货单失败：" + e.getMessage(), e);
        }
        g.dispose();
        return img;
    }

    /** 标定页光栅：全页刻度（每 10mm）+ 边框 + 对角线 + 中心十字，位置只受偏移校准影响 */
    private byte[] renderTestPageToEscp(PaperPresets.Preset preset, double offXmm, double offYmm) {
        return toEscp(renderTestPageToImage(preset, offXmm, offYmm));
    }

    private BufferedImage renderTestPageToImage(PaperPresets.Preset preset, double offXmm, double offYmm) {
        double scale = RASTER_DPI / 72.0;
        double mmPx = RASTER_DPI / 25.4;
        int w = (int) Math.round(preset.widthPt() * scale);
        int bands = (int) Math.floor(preset.heightPt() * scale / 24.0);
        int h = bands * 24;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.translate(offXmm * mmPx, offYmm * mmPx);
        g.setColor(Color.BLACK);
        g.setFont(new Font("宋体", Font.PLAIN, (int) Math.round(4 * mmPx)));
        for (int m = 0; m * mmPx < w; m += 10) {
            int x = (int) Math.round(m * mmPx);
            g.drawLine(x, 0, x, (int) Math.round(5 * mmPx));
            g.drawString(String.valueOf(m), x + 1, (int) Math.round(9 * mmPx));
        }
        for (int m = 0; m * mmPx < h; m += 10) {
            int y = (int) Math.round(m * mmPx);
            g.drawLine(0, y, (int) Math.round(5 * mmPx), y);
            g.drawString(String.valueOf(m), (int) Math.round(6 * mmPx), y + (int) Math.round(3 * mmPx));
        }
        g.drawRect(0, 0, w - 1, h - 1);
        g.drawLine(0, 0, w - 1, h - 1);
        g.drawLine(w - 1, 0, 0, h - 1);
        int cx = w / 2, cy = h / 2;
        g.drawLine(cx - 30, cy, cx + 30, cy);
        g.drawLine(cx, cy - 30, cx, cy + 30);
        g.drawString("标定页 「0」刻度应与纸的左上角重合", (int) Math.round(20 * mmPx), (int) Math.round(25 * mmPx));
        // 底部实心黑条（全宽 241mm × 5mm）：横贯整张纸宽 = 打印点距正确
        g.fillRect(0, h - (int) Math.round(5 * mmPx), w, (int) Math.round(5 * mmPx));
        g.dispose();
        return img;
    }

    /** 调试：导出发往打印机的光栅图（PNG），肉眼核对渲染结果 */
    @Override
    public byte[] debugRaster(String type, Long orderId) throws Exception {
        Map<String, String> config = configService.getGroup(GROUP);
        PaperPresets.Preset preset = PaperPresets.of(PaperPresets.isValid(config.get("paper"))
                ? config.get("paper") : PaperPresets.DEFAULT_KEY);
        double offXmm = parseDouble(config.get("offsetXmm"));
        double offYmm = parseDouble(config.get("offsetYmm"));
        BufferedImage img;
        if ("test".equals(type)) {
            img = renderTestPageToImage(preset, offXmm, offYmm);
        } else {
            if (orderId == null) {
                throw new IllegalArgumentException("送货单光栅需要 orderId");
            }
            DeliveryNote note = deliveryNoteService.buildForSaleOrder(orderId);
            String fontFamily = firstNonBlank(config.get("font"), "宋体");
            int fontSize = parseInt(config.get("fontSize"), 9);
            DeliveryNoteRenderer renderer = new DeliveryNoteRenderer(note, preset, fontFamily, fontSize);
            img = renderNoteToImage(note, preset, renderer, offXmm, offYmm);
        }
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", png);
        return png.toByteArray();
    }

    /** 光栅图 → ESC/P2 点阵指令流（180dpi、24 点带；页长设 12 英寸防止中途自动进页） */
    private byte[] toEscp(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int bands = (int) Math.ceil(h / 24.0);
        ByteArrayOutputStream out = new ByteArrayOutputStream(w * 3 * bands);
        // 初始化 + 页长 = 纸高（ESC C n：n 行 × 1/6 英寸；140mm ≈ 33 行 = 139.7mm），
        // 保证换页(FF)精确落在下一张连续纸的开头
        writeBytes(out, new int[]{0x1B, '@', 0x1B, 'C', 33});
        for (int b = 0; b < bands; b++) {
            out.write(0x1B);
            out.write('*');
            out.write(32);                      // 180dpi 24 点带模式
            out.write(w & 0xFF);
            out.write((w >> 8) & 0xFF);
            for (int x = 0; x < w; x++) {
                for (int seg = 0; seg < 3; seg++) {
                    int bits = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int y = b * 24 + seg * 8 + bit;
                        if (y < h && img.getRGB(x, y) < -1_000_000) {
                            bits |= (0x80 >> bit);
                        }
                    }
                    out.write(bits);
                }
            }
            out.write(0x0D);                    // 回车（打印头回到行首）
            writeBytes(out, new int[]{0x1B, 'J', 24}); // 走纸 24/180 英寸
        }
        out.write(0x0C);                        // 换页出纸
        return out.toByteArray();
    }

    private void writeBytes(ByteArrayOutputStream out, int[] bytes) {
        for (int b : bytes) {
            out.write(b);
        }
    }

    /** RAW 字节流直发打印机（RAW 数据类型透传到端口，不做任何 GDI 渲染与坐标变换） */
    private void sendRawWithTimeout(PrintService service, byte[] data) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> {
                try {
                    DocPrintJob job = service.createPrintJob();
                    Doc doc = new SimpleDoc(data, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
                    job.print(doc, new HashPrintRequestAttributeSet());
                } catch (PrintException e) {
                    throw new IllegalStateException("RAW 直打失败：" + e.getMessage(), e);
                }
            });
            future.get(printTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("RAW 直打超时（" + printTimeoutSeconds + "s）：请检查打印机电源与联机状态");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(cause.getMessage() != null ? cause.getMessage() : "RAW 直打失败", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean parseBool(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.equalsIgnoreCase("true") || value.equals("1");
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
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

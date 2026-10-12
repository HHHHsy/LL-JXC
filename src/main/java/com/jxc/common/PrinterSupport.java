package com.jxc.common;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.PrinterMakeAndModel;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * 打印机探测与打印任务守护。
 *
 * <p>为什么需要它：Windows 上「默认打印机」经常是 <b>Microsoft Print to PDF</b> / <b>导出为WPS PDF</b>
 * 这类「打印到文件」的虚拟打印机。这种打印机不会出纸，而是弹出一个"另存为"对话框，
 * 后端打印线程会一直等在那里不返回，前端只能看到 <i>timeout of 10000ms exceeded</i> 这种
 * 什么也说明不了的错误。所以直连打印前必须先把"能不能真的出纸"判断清楚：
 * 没有打印机、打印机名字对不上、选中了虚拟打印机，都要提前给出明确提示。</p>
 *
 * <p>同时提供 {@link #runWithTimeout}：即使打印机真的卡住（离线/缺纸/驱动弹窗），
 * 也在配置的超时时间内中断并返回可读的错误，而不是让 HTTP 请求无限等待。</p>
 */
public final class PrinterSupport {

    /** 虚拟打印机（打印到文件）特征：名称或驱动型号里命中这些词就不认为它能直接出纸 */
    private static final Pattern VIRTUAL = Pattern.compile(
            "(?i)(pdf|xps|onenote|fax|virtual|print\\s*to\\s*file|打印到文件|导出为|虚拟|传真)");

    /** 一台打印机的探测结果 */
    public record Info(String name, boolean isDefault, boolean virtual, String model) {
    }

    private PrinterSupport() {
    }

    /** 枚举本机打印机（含是否系统默认、是否虚拟打印机）；枚举失败时返回空列表而不是抛异常 */
    public static List<Info> list() {
        String defaultName = null;
        try {
            PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
            defaultName = defaultService == null ? null : defaultService.getName();
        } catch (Exception ignored) {
            // 拿不到默认打印机不影响列表
        }
        List<Info> result = new ArrayList<>();
        for (PrintService service : services()) {
            String name = service.getName();
            String model = model(service);
            result.add(new Info(name, name != null && name.equalsIgnoreCase(defaultName),
                    isVirtual(name, model), model));
        }
        return result;
    }

    public static PrintService[] services() {
        try {
            return PrintServiceLookup.lookupPrintServices(null, null);
        } catch (Exception e) {
            return new PrintService[0];
        }
    }

    /** 按名称找打印机（忽略大小写与首尾空白），找不到返回 null */
    public static PrintService resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String target = name.trim();
        for (PrintService service : services()) {
            if (service.getName() != null && service.getName().equalsIgnoreCase(target)) {
                return service;
            }
        }
        return null;
    }

    public static Info find(List<Info> printers, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String target = name.trim();
        for (Info info : printers) {
            if (info.name() != null && info.name().equalsIgnoreCase(target)) {
                return info;
            }
        }
        return null;
    }

    /** 名称或驱动型号命中虚拟打印机特征 */
    public static boolean isVirtual(String name, String model) {
        return matches(name) || matches(model);
    }

    /** 是否所有打印机都是虚拟打印机（本机压根没有能出纸的打印机） */
    public static boolean allVirtual(List<Info> printers) {
        if (printers.isEmpty()) {
            return false;
        }
        for (Info info : printers) {
            if (!info.virtual()) {
                return false;
            }
        }
        return true;
    }

    public static String joinNames(List<Info> printers) {
        StringBuilder sb = new StringBuilder();
        for (Info info : printers) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(info.name());
            if (info.virtual()) {
                sb.append("（虚拟打印机）");
            }
        }
        return sb.toString();
    }

    /**
     * 送打印任务并限时等待（不带打印属性）。
     *
     * @see #runWithTimeout(PrinterJob, PrintRequestAttributeSet, int, String)
     */
    public static void runWithTimeout(PrinterJob job, int timeoutSeconds, String printerName) throws PrinterException {
        runWithTimeout(job, null, timeoutSeconds, printerName);
    }

    /**
     * 送打印任务并限时等待：打印机卡住时中断任务并抛出可读的 {@link PrinterException}，
     * 避免请求线程被无限期挂住（前端只会看到一句无意义的超时）。
     *
     * @param attributes 送印属性（如打印分辨率），为空则用驱动默认
     */
    public static void runWithTimeout(PrinterJob job, PrintRequestAttributeSet attributes,
                                      int timeoutSeconds, String printerName) throws PrinterException {
        int seconds = timeoutSeconds > 0 ? timeoutSeconds : 20;
        ExecutorService pool = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "delivery-note-print");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<?> future = pool.submit(() -> {
                if (attributes == null) {
                    job.print();
                } else {
                    job.print(attributes);
                }
                return null;
            });
            future.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            job.cancel();
            futureCancel(pool);
            throw new PrinterException("打印机「" + printerName + "」" + seconds
                    + " 秒内没有响应：请检查打印机是否已开机、在线、有纸，或驱动是否弹出了对话框（虚拟 PDF 打印机会等" +
                    "「另存为」窗口），处理后在「系统设置 → 打印设置」重新确认打印机再试");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PrinterException pe) {
                throw pe;
            }
            throw new PrinterException(cause == null || cause.getMessage() == null
                    ? "打印机返回未知错误" : cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PrinterException("打印被中断，请重试");
        } finally {
            pool.shutdownNow();
        }
    }

    private static void futureCancel(ExecutorService pool) {
        try {
            pool.shutdownNow();
        } catch (Exception ignored) {
            // 打断失败不影响返回结果
        }
    }

    private static boolean matches(String text) {
        return text != null && VIRTUAL.matcher(text).find();
    }

    /** 驱动型号（拿不到就返回空串），用于区分真实打印机与虚拟打印机 */
    private static String model(PrintService service) {
        try {
            PrintServiceAttributeSet attributes = service.getAttributes();
            Object value = attributes.get(PrinterMakeAndModel.class);
            return value == null ? "" : value.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }
}

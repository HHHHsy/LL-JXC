package com.jxc.common;

import com.jxc.dto.DeliveryNote;
import com.jxc.dto.DeliveryNoteItem;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 送货单版式渲染（后端直连打印 / 预览图共用同一套绘制逻辑）。
 *
 * <p>坐标单位是点（1pt=1/72 英寸），绘制区域为纸张的可打印区；
 * 表格按单元格绘制（含跨列合并），因此竖线、横线与跨列文字都能正确呈现。</p>
 */
public class DeliveryNoteRenderer implements Printable {

    /** 列宽比例：序号 | 产品名称 | 型号规格 | 数量 | 单位 | 单价 | 金额 | 备注 */
    private static final double[] WIDTH_RATIO = {0.05, 0.26, 0.18, 0.07, 0.07, 0.10, 0.11, 0.16};

    private static final int ALIGN_LEFT = 0;
    private static final int ALIGN_CENTER = 1;
    private static final int ALIGN_RIGHT = 2;

    private final DeliveryNote note;
    private final PaperPresets.Preset preset;
    private final String fontFamily;
    private final int baseFontSize;
    private final boolean antialias;

    public DeliveryNoteRenderer(DeliveryNote note, PaperPresets.Preset preset,
                                String fontFamily, int baseFontSize) {
        this.note = note;
        this.preset = preset;
        this.fontFamily = resolveFontFamily(fontFamily);
        this.baseFontSize = baseFontSize <= 0 ? 9 : baseFontSize;
        // 针式打印机不做抗锯齿，点阵输出更清晰
        this.antialias = !preset.dotMatrix();
    }

    /** 单元格定义：跨列数 + 文本 + 对齐 + 是否加粗 */
    private static final class Cell {
        private final int span;
        private final String text;
        private final int align;
        private final boolean bold;

        private Cell(int span, String text, int align, boolean bold) {
            this.span = span;
            this.text = text == null ? "" : text;
            this.align = align;
            this.bold = bold;
        }

        static Cell of(String text) {
            return new Cell(1, text, ALIGN_LEFT, false);
        }

        static Cell center(String text) {
            return new Cell(1, text, ALIGN_CENTER, false);
        }

        static Cell right(String text) {
            return new Cell(1, text, ALIGN_RIGHT, false);
        }

        static Cell span(int span, String text, int align, boolean bold) {
            return new Cell(span, text, align, bold);
        }
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        Graphics2D g = (Graphics2D) graphics;
        g.setColor(Color.BLACK);

        double width = pageFormat.getImageableWidth();
        double height = pageFormat.getImageableHeight();
        double rowHeight = rowHeight();
        double tableHeadHeight = rowHeight * 1.15;

        // 分页：首页有抬头，续页用简短抬头
        double firstFixed = rowHeight * 4 + tableHeadHeight + rowHeight * 4;
        int rowsFirstPage = Math.max(1, (int) Math.floor((height - firstFixed) / rowHeight));
        double otherFixed = rowHeight * 2 + tableHeadHeight + rowHeight * 4;
        int rowsOtherPage = Math.max(1, (int) Math.floor((height - otherFixed) / rowHeight));

        List<DeliveryNoteItem> items = note.getItems();
        int total = items.size();
        int start;
        int rowsThisPage;
        if (pageIndex == 0) {
            start = 0;
            rowsThisPage = Math.min(rowsFirstPage, total);
        } else {
            start = rowsFirstPage + (pageIndex - 1) * rowsOtherPage;
            if (start >= total) {
                return NO_SUCH_PAGE;
            }
            rowsThisPage = Math.min(rowsOtherPage, total - start);
        }
        boolean lastPage = start + rowsThisPage >= total;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                antialias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                antialias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        double y = pageIndex == 0
                ? drawFirstPageHeader(g, width, rowHeight)
                : drawContinueHeader(g, width, rowHeight, pageIndex + 1);

        // 表头
        drawCells(g, width, y, tableHeadHeight, true,
                Cell.center("序号"), Cell.center("产品名称"), Cell.center("型号规格"), Cell.center("数量"),
                Cell.center("单位"), Cell.center("单价"), Cell.center("金额"), Cell.center("备注"));
        y += tableHeadHeight;

        // 明细
        for (int i = 0; i < rowsThisPage; i++) {
            DeliveryNoteItem item = items.get(start + i);
            if (item.isBlank()) {
                drawCells(g, width, y, rowHeight, false,
                        Cell.center(""), Cell.of(""), Cell.of(""), Cell.center(""),
                        Cell.center(""), Cell.right(""), Cell.right(""), Cell.of(""));
            } else {
                drawCells(g, width, y, rowHeight, false,
                        Cell.center(String.valueOf(item.getSeq())),
                        Cell.of(item.getProductName()),
                        Cell.of(item.getSpec()),
                        Cell.center(item.getQuantity() == null ? "" : String.valueOf(item.getQuantity())),
                        Cell.center(item.getUnit()),
                        Cell.right(money(item.getPrice())),
                        Cell.right(money(item.getAmount())),
                        Cell.of(item.getRemark()));
            }
            y += rowHeight;
        }

        if (lastPage) {
            drawTotalRow(g, width, y, rowHeight);
        } else {
            g.setFont(font(Font.PLAIN, baseFontSize));
            drawCells(g, width, y, rowHeight, false,
                    Cell.span(8, "（明细续下页）", ALIGN_CENTER, false));
        }
        y += rowHeight;

        // 页脚：与纸质单据一致，跨列合并，避免文字被列宽截断
        g.setFont(font(Font.PLAIN, baseFontSize));
        drawCells(g, width, y, rowHeight, false,
                Cell.span(4, "送货人：" + nvl(note.getDeliveryMan()), ALIGN_LEFT, false),
                Cell.span(4, "收货人姓名：", ALIGN_LEFT, false));
        y += rowHeight;
        drawCells(g, width, y, rowHeight, false,
                Cell.span(8, "备注：" + nvl(note.getFooterNote()), ALIGN_LEFT, false));
        y += rowHeight;
        drawCells(g, width, y, rowHeight, false,
                Cell.span(4, "服务电话：" + nvl(note.getServicePhone()), ALIGN_LEFT, false),
                Cell.span(4, "联系地址：" + nvl(note.getServiceAddress()), ALIGN_LEFT, false));

        return PAGE_EXISTS;
    }

    private double drawFirstPageHeader(Graphics2D g, double width, double rowHeight) {
        double y = 0;
        g.setFont(font(Font.BOLD, (int) Math.round(baseFontSize * 2.1)));
        drawCentered(g, nvl(note.getCompanyName()), width, y + baseFontSize * 2.1);
        y += baseFontSize * 2.2;
        g.setFont(font(Font.BOLD, (int) Math.round(baseFontSize * 1.6)));
        drawCentered(g, nvl(note.getTitle()), width, y + baseFontSize * 1.5);
        y += baseFontSize * 1.7;
        y += (rowHeight * 2 - (baseFontSize * 2.2 + baseFontSize * 1.7)) / 2;

        g.setFont(font(Font.PLAIN, baseFontSize));
        drawText(g, "客户名称：" + nvl(note.getCustomerName()), 0, y + rowHeight * 0.72);
        drawText(g, "地址：" + nvl(note.getCustomerAddress()), width * 0.52, y + rowHeight * 0.72);
        y += rowHeight;

        String date = note.getSaleDate() == null ? ""
                : new SimpleDateFormat("yyyy/M/d").format(java.sql.Date.valueOf(note.getSaleDate()));
        drawText(g, "电话：" + nvl(note.getCustomerPhone()), 0, y + rowHeight * 0.72);
        drawText(g, "送货日期：" + date, width * 0.52, y + rowHeight * 0.72);
        drawText(g, "单据编号：" + nvl(note.getOrderNo()), width * 0.78, y + rowHeight * 0.72);
        y += rowHeight;
        return y;
    }

    private double drawContinueHeader(Graphics2D g, double width, double rowHeight, int pageNo) {
        double y = 0;
        g.setFont(font(Font.BOLD, (int) Math.round(baseFontSize * 1.3)));
        drawCentered(g, nvl(note.getCompanyName()) + "  " + nvl(note.getTitle()) + "（续页 " + pageNo + "）",
                width, y + rowHeight * 0.8);
        y += rowHeight;
        g.setFont(font(Font.PLAIN, baseFontSize));
        drawText(g, "客户：" + nvl(note.getCustomerName()), 0, y + rowHeight * 0.72);
        drawText(g, "单据编号：" + nvl(note.getOrderNo()), width * 0.6, y + rowHeight * 0.72);
        y += rowHeight;
        return y;
    }

    /** 合计行：合计 |（数量）| 金额大写（跨列）| 金额小写 */
    private void drawTotalRow(Graphics2D g, double width, double y, double rowHeight) {
        double[] x = columnX(width);
        // 合计（跨 序号~型号规格 3 列）
        g.setFont(font(Font.BOLD, baseFontSize));
        g.drawRect((int) Math.round(x[0]), (int) Math.round(y),
                (int) Math.round(x[3] - x[0]), (int) Math.round(rowHeight));
        drawTextCenteredInCell(g, "合计", x[0], x[3], y, rowHeight);
        // 数量合计
        g.drawRect((int) Math.round(x[3]), (int) Math.round(y),
                (int) Math.round(x[4] - x[3]), (int) Math.round(rowHeight));
        drawTextCenteredInCell(g, String.valueOf(note.getTotalQuantity()), x[3], x[4], y, rowHeight);
        // 金额大写（跨 单位~单价 列，字号略小以便放得下）
        g.drawRect((int) Math.round(x[4]), (int) Math.round(y),
                (int) Math.round(x[6] - x[4]), (int) Math.round(rowHeight));
        g.setFont(font(Font.PLAIN, Math.max(7, baseFontSize - 1)));
        drawTextLeftInCell(g, "大写：" + nvl(note.getTotalAmountUpper()), x[4], x[6], y, rowHeight);
        // 金额小写（跨 金额~备注 列）
        g.setFont(font(Font.BOLD, baseFontSize));
        g.drawRect((int) Math.round(x[6]), (int) Math.round(y),
                (int) Math.round(width - x[6]), (int) Math.round(rowHeight));
        drawTextRightInCell(g, money(note.getTotalAmount()), x[6], width, y, rowHeight);
    }

    /** 按单元格绘制：每个单元格画自己的矩形，于是竖线与横线都完整；支持跨列 */
    private void drawCells(Graphics2D g, double width, double y, double rowHeight, boolean bold, Cell... cells) {
        double[] x = columnX(width);
        int column = 0;
        for (Cell cell : cells) {
            int endColumn = Math.min(column + cell.span, x.length - 1);
            double x1 = x[column];
            double x2 = x[endColumn];
            g.drawRect((int) Math.round(x1), (int) Math.round(y),
                    (int) Math.round(x2 - x1), (int) Math.round(rowHeight));
            g.setFont(font(bold || cell.bold ? Font.BOLD : Font.PLAIN, baseFontSize));
            switch (cell.align) {
                case ALIGN_CENTER -> drawTextCenteredInCell(g, cell.text, x1, x2, y, rowHeight);
                case ALIGN_RIGHT -> drawTextRightInCell(g, cell.text, x1, x2, y, rowHeight);
                default -> drawTextLeftInCell(g, cell.text, x1, x2, y, rowHeight);
            }
            column = endColumn;
        }
        // 补齐未覆盖的列，保证表格底部横线完整
        while (column < x.length - 1) {
            g.drawRect((int) Math.round(x[column]), (int) Math.round(y),
                    (int) Math.round(x[column + 1] - x[column]), (int) Math.round(rowHeight));
            column++;
        }
    }

    private double[] columnX(double width) {
        double[] x = new double[WIDTH_RATIO.length + 1];
        double acc = 0;
        for (int i = 0; i < WIDTH_RATIO.length; i++) {
            x[i] = acc;
            acc += width * WIDTH_RATIO[i];
        }
        x[WIDTH_RATIO.length] = width;
        return x;
    }

    private void drawText(Graphics2D g, String text, double x, double baseline) {
        g.drawString(nvl(text), (float) x, (float) baseline);
    }

    private void drawCentered(Graphics2D g, String text, double width, double baseline) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int textWidth = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (float) Math.max(0, (width - textWidth) / 2), (float) baseline);
    }

    private void drawTextLeftInCell(Graphics2D g, String text, double x1, double x2, double y, double rowHeight) {
        if (text == null || text.isEmpty()) {
            return;
        }
        g.drawString(fit(g, text, x2 - x1 - 4), (float) (x1 + 2), (float) (y + rowHeight * 0.72));
    }

    private void drawTextRightInCell(Graphics2D g, String text, double x1, double x2, double y, double rowHeight) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int w = g.getFontMetrics().stringWidth(text);
        double x = Math.max(x1 + 2, x2 - 3 - w);
        g.drawString(text, (float) x, (float) (y + rowHeight * 0.72));
    }

    private void drawTextCenteredInCell(Graphics2D g, String text, double x1, double x2, double y, double rowHeight) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String fitted = fit(g, text, x2 - x1 - 4);
        int w = g.getFontMetrics().stringWidth(fitted);
        g.drawString(fitted, (float) (x1 + Math.max(1, (x2 - x1 - w) / 2)), (float) (y + rowHeight * 0.72));
    }

    /** 超宽文本截断（保留省略号），避免压到下一列 */
    private String fit(Graphics2D g, String text, double maxWidth) {
        if (g.getFontMetrics().stringWidth(text) <= maxWidth) {
            return text;
        }
        int len = text.length();
        while (len > 1) {
            String candidate = text.substring(0, len) + "…";
            if (g.getFontMetrics().stringWidth(candidate) <= maxWidth) {
                return candidate;
            }
            len--;
        }
        return text.substring(0, 1);
    }

    private double rowHeight() {
        return baseFontSize * 1.9;
    }

    private Font font(int style, int size) {
        return new Font(fontFamily, style, Math.max(6, size));
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    /** 中文字体：优先使用配置字体，系统没有则回落到可用中文字体 */
    private static String resolveFontFamily(String preferred) {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        if (preferred != null && !preferred.isBlank() && available.contains(preferred)) {
            return preferred;
        }
        for (String candidate : new String[]{"宋体", "SimSun", "Microsoft YaHei", "微软雅黑"}) {
            if (available.contains(candidate)) {
                return candidate;
            }
        }
        return Font.SANS_SERIF;
    }

    /** 渲染成图片（自检/预览用，不经过打印机） */
    public BufferedImage renderToImage(int dpi) {
        double scale = dpi / 72.0;
        int width = (int) Math.round(preset.widthPt() * scale);
        int height = (int) Math.round(preset.heightPt() * scale);
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.translate(preset.marginLeftMm() * PaperPresets.MM_TO_PT * scale,
                preset.marginTopMm() * PaperPresets.MM_TO_PT * scale);
        g.setClip(0, 0, (int) Math.round(preset.imageableWidthPt() * scale),
                (int) Math.round(preset.imageableHeightPt() * scale));
        g.scale(scale, scale);
        try {
            print(g, buildPageFormat(preset), 0);
        } catch (PrinterException e) {
            throw new IllegalStateException("渲染送货单失败：" + e.getMessage(), e);
        }
        g.dispose();
        return image;
    }

    public static PageFormat buildPageFormat(PaperPresets.Preset preset) {
        java.awt.print.Paper paper = new java.awt.print.Paper();
        paper.setSize(preset.widthPt(), preset.heightPt());
        paper.setImageableArea(preset.marginLeftMm() * PaperPresets.MM_TO_PT,
                preset.marginTopMm() * PaperPresets.MM_TO_PT,
                preset.imageableWidthPt(), preset.imageableHeightPt());
        PageFormat pageFormat = new PageFormat();
        pageFormat.setPaper(paper);
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        return pageFormat;
    }

    /** 打印总页数（按当前纸张估算），供界面提示 */
    public int estimatePages() {
        double height = preset.imageableHeightPt();
        double rowHeight = rowHeight();
        double tableHead = rowHeight * 1.15;
        int rowsFirst = Math.max(1, (int) Math.floor((height - (rowHeight * 4 + tableHead + rowHeight * 4)) / rowHeight));
        int rowsOther = Math.max(1, (int) Math.floor((height - (rowHeight * 2 + tableHead + rowHeight * 4)) / rowHeight));
        List<DeliveryNoteItem> items = note.getItems();
        if (items.size() <= rowsFirst) {
            return 1;
        }
        return 1 + (int) Math.ceil((items.size() - rowsFirst) / (double) rowsOther);
    }

    /** 便于测试：当前版式使用的行高 */
    public double rowHeightForTest() {
        return rowHeight();
    }

    /** 便于测试：列边界 */
    public List<Double> columnBoundariesForTest(double width) {
        double[] x = columnX(width);
        List<Double> list = new ArrayList<>();
        for (double v : x) {
            list.add(v);
        }
        return list;
    }
}

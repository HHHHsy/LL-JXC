package com.jxc.common;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;

/**
 * Excel 导入模板生成：模板中的表头与导入解析器使用的列关键字保持一致，
 * 用户直接下载填写即可，不会出现「列名对不上」的问题。
 */
public final class ExcelTemplate {

    private ExcelTemplate() {
    }

    /** 商品导入模板 */
    public static byte[] productTemplate() {
        String[] headers = {"商品编码", "商品名称", "商品分类", "规格型号", "单位", "成本价", "销售价"};
        String[][] samples = {
                {"SP0001", "示例商品-瓦尔塔星标B24-45-L-T1-M", "电池类", "(55B24L细)18个月", "台", "225", "299"},
                {"", "示例商品-名称必填，编码可留空自动生成", "配件类", "通用", "个", "12.5", "19.9"},
                {"SP9001", "示例商品-带编码的行会校验重复", "", "", "", "", ""}
        };
        String[] notes = {
                "1. 第 1 行是表头，请勿删除或改名；列顺序可以调整，程序按列名识别。",
                "2. 「商品名称」必填；其余列可留空。",
                "3. 「商品编码」留空时系统自动生成（SP + 序号）；填写的编码若已存在，该行会报错。",
                "4. 「商品分类」「规格型号」「单位」为文本；「成本价」「销售价」为数字，可带两位小数，留空视为 0。",
                "5. 库存数量不在此导入（由采购入库、销售出库、库存盘点自动维护）。",
                "6. 空行会自动跳过；带「合计/总计」字样的行会被识别为汇总行并跳过。",
                "7. 导入前会先「解析预览」，逐行显示校验结果，确认无误后再执行导入。"
        };
        return build("商品导入", headers, samples, notes);
    }

    /** 采购清单导入模板 */
    public static byte[] purchaseTemplate() {
        String[] headers = {"序号", "采购型号名称", "参考规格型号", "单位", "数量", "单价(元)", "金额(元)", "参考适合车型"};
        String[][] samples = {
                {"1", "示例-瓦尔塔星标B24-45-L-T1-M", "(55B24L细)18个月", "台", "3", "225", "675", "丰田卡罗拉、本田飞度"},
                {"2", "示例-鸿雁6-QW-60L南", "(55D26L南)", "台", "3", "200", "600", "马自达6、奔腾B70"}
        };
        String[] notes = {
                "1. 第 1 行是表头，请勿删除或改名；列顺序可以调整，程序按列名识别。",
                "2. 「采购型号名称」「数量」必填；「单价(元)」用于写入采购成本价。",
                "3. 「金额(元)」可留空；填写时用于核对「数量×单价」，不一致会在预览中提示（以单价为准）。",
                "4. 「参考适合车型」会写入采购明细备注，不丢字段。",
                "5. 商品按「名称+规格」匹配系统已有商品；匹配不到时可选择自动建档（编码自动生成）。",
                "6. 空行与「合计/总计」行会自动跳过。",
                "7. 一次导入生成一张采购订单，供应商与采购日期在界面上选择。"
        };
        return build("采购清单", headers, samples, notes);
    }

    private static byte[] build(String sheetName, String[] headers, String[][] samples, String[] notes) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(sheetName);
            XSSFCellStyle headStyle = headerStyle(workbook);
            XSSFCellStyle wrapStyle = wrapStyle(workbook);

            Row head = sheet.createRow(0);
            head.setHeightInPoints(22);
            for (int i = 0; i < headers.length; i++) {
                head.createCell(i).setCellValue(headers[i]);
                head.getCell(i).setCellStyle(headStyle);
                sheet.setColumnWidth(i, Math.max(12, headers[i].length() * 2 + 10) * 256);
            }
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < headers.length; c++) {
                    String value = c < samples[r].length ? samples[r][c] : "";
                    row.createCell(c).setCellValue(value);
                }
            }

            XSSFSheet noteSheet = workbook.createSheet("填写说明");
            noteSheet.setColumnWidth(0, 120 * 256);
            noteSheet.createRow(0).createCell(0).setCellValue("填写说明");
            noteSheet.getRow(0).getCell(0).setCellStyle(headStyle);
            for (int i = 0; i < notes.length; i++) {
                Row row = noteSheet.createRow(i + 1);
                row.createCell(0).setCellValue(notes[i]);
                row.getCell(0).setCellStyle(wrapStyle);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("生成 Excel 模板失败：" + e.getMessage(), e);
        }
    }

    private static XSSFCellStyle headerStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static XSSFCellStyle wrapStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
}

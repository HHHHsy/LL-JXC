package com.jxc.common;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 解析公共支撑层：把「表头定位、单元格读取、汇总行识别」等通用逻辑收在一处，
 * 供采购清单导入、商品导入等复用，避免每个导入功能各写一份。
 *
 * <p>约定：表头用<b>中文列名关键字</b>识别，因此列顺序可以调整、表头可以不在第一行。</p>
 */
public final class ExcelSupport {

    /** 合计/汇总行关键字 */
    private static final String[] SUMMARY_WORDS = {"合计", "总计", "小计", "合 计"};

    private ExcelSupport() {
    }

    /** 一列的定义：逻辑名 + 表头关键字 + 是否必需 */
    public static final class Column {
        private final String key;
        private final String displayName;
        private final String[] keywords;
        private final boolean required;

        public Column(String key, String displayName, boolean required, String... keywords) {
            this.key = key;
            this.displayName = displayName;
            this.required = required;
            this.keywords = keywords;
        }
    }

    /** 表头定位结果 */
    public static final class Header {
        private final int headerRowIndex;
        private final Map<String, Integer> columns;

        Header(int headerRowIndex, Map<String, Integer> columns) {
            this.headerRowIndex = headerRowIndex;
            this.columns = columns;
        }

        public int getHeaderRowIndex() {
            return headerRowIndex;
        }

        /** 取列号，未识别到返回 null */
        public Integer col(String key) {
            return columns.get(key);
        }
    }

    /** 工作表读取器 */
    public static final class Reader implements AutoCloseable {
        private final Workbook workbook;
        private final Sheet sheet;
        private final DataFormatter formatter = new DataFormatter();
        private final FormulaEvaluator evaluator;

        private Reader(Workbook workbook) {
            this.workbook = workbook;
            this.sheet = workbook.getSheetAt(0);
            this.evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        }

        public static Reader open(InputStream in) throws Exception {
            return new Reader(WorkbookFactory.create(in));
        }

        public String sheetName() {
            return sheet.getSheetName();
        }

        /** 最后一行下标（0 基） */
        public int lastRowIndex() {
            return sheet.getLastRowNum();
        }

        public Row row(int index) {
            return sheet.getRow(index);
        }

        public String text(Row row, Integer col) {
            if (row == null || col == null) {
                return "";
            }
            Cell cell = row.getCell(col);
            if (cell == null) {
                return "";
            }
            String value = formatter.formatCellValue(cell, evaluator);
            return value == null ? "" : value.trim();
        }

        /** 读取数字：数字型单元格直接取值，文本型去掉千分位/货币符号后解析；无法解析返回 null */
        public BigDecimal decimal(Row row, Integer col) {
            if (row == null || col == null) {
                return null;
            }
            Cell cell = row.getCell(col);
            if (cell == null) {
                return null;
            }
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            if (cell.getCellType() == CellType.FORMULA && evaluator.evaluateFormulaCell(cell) == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            String value = text(row, col)
                    .replace(",", "").replace("，", "")
                    .replace("￥", "").replace("¥", "").replace("元", "").trim();
            if (value.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** 整行文本拼接，用于判断是否汇总行 */
        public String join(Row row) {
            if (row == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            int last = row.getLastCellNum();
            for (int c = 0; c < last; c++) {
                sb.append(text(row, c));
            }
            return sb.toString();
        }

        @Override
        public void close() {
            try {
                workbook.close();
            } catch (Exception ignored) {
                // 关闭失败不影响业务
            }
        }
    }

    /**
     * 在前 {@code maxScanRows} 行内寻找表头行并映射列号。
     *
     * @throws RuntimeException 必需列缺失时抛出，消息中指明确缺哪一列
     */
    public static Header locateHeader(Reader reader, List<Column> columns, int maxScanRows) {
        int limit = Math.min(maxScanRows, reader.lastRowIndex() + 1);
        Map<String, Integer> best = new LinkedHashMap<>();
        int bestRow = -1;
        for (int i = 0; i < limit; i++) {
            Row row = reader.row(i);
            if (row == null) {
                continue;
            }
            Map<String, Integer> found = new HashMap<>();
            int last = row.getLastCellNum();
            for (int c = 0; c < last; c++) {
                String header = reader.text(row, c).replace(" ", "");
                if (header.isEmpty()) {
                    continue;
                }
                for (Column column : columns) {
                    if (!found.containsKey(column.key) && matchesAny(header, column.keywords)) {
                        found.put(column.key, c);
                        break;
                    }
                }
            }
            boolean allRequired = true;
            for (Column column : columns) {
                if (column.required && !found.containsKey(column.key)) {
                    allRequired = false;
                    break;
                }
            }
            if (allRequired) {
                Map<String, Integer> result = new LinkedHashMap<>();
                for (Column column : columns) {
                    if (found.containsKey(column.key)) {
                        result.put(column.key, found.get(column.key));
                    }
                }
                return new Header(i, result);
            }
            if (bestRow < 0 && !found.isEmpty()) {
                best = found;
                bestRow = i;
            }
        }
        // 报错时指出缺哪一列，便于用户对照修改
        List<String> missing = new ArrayList<>();
        for (Column column : columns) {
            if (column.required && !best.containsKey(column.key)) {
                missing.add(column.displayName);
            }
        }
        if (missing.isEmpty()) {
            missing.add(columns.get(0).displayName);
        }
        throw new RuntimeException("未能在表格中找到必需列：" + String.join("、", missing) + "，请检查表头（可在「下载示例」中查看标准格式）");
    }

    public static boolean isSummaryRow(String joined) {
        if (joined == null || joined.isEmpty()) {
            return false;
        }
        for (String word : SUMMARY_WORDS) {
            if (joined.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesAny(String header, String[] keys) {
        for (String key : keys) {
            if (header.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** 名称/规格的归一化键：去空白 + 转小写 */
    public static String key(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    public static String key(String name, String spec) {
        return key(name) + "|" + key(spec);
    }
}

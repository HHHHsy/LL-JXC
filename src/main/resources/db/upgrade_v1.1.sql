-- =============================================
-- LL进销存系统 升级脚本：v1.0 -> v1.1
-- =============================================
-- 用途：把旧版本建的库升级到当前版本。
--
-- 说明：`init.sql` 现在已经是**幂等且非破坏性**的（不含 DROP/TRUNCATE，建表用 IF NOT EXISTS，
--       并自带下面的字段补齐与历史数据修复），因此直接重复执行 `init.sql` 也能完成升级。
--       本脚本保留给希望"只做升级、不建表"的场景，两者可任选其一。
--
-- 本脚本不含任何 DROP / TRUNCATE / DELETE 语句，可安全重复执行。
-- 执行方式：mysql --default-character-set=utf8mb4 -u root -p < upgrade_v1.1.sql
-- =============================================

USE jxc_db;

-- ---------------------------------------------
-- 1) 商品表增加乐观锁版本号（防止并发出入库丢失更新）
--    MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里先查 information_schema 再动态执行，
--    因此重复执行不会报 "Duplicate column name"。
-- ---------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'product'
                      AND COLUMN_NAME = 'version');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE product ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER stock_quantity',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------
-- 1.1) 采购明细增加备注列（Excel 导入时写入「参考适合车型」）
-- ---------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'purchase_order_detail'
                      AND COLUMN_NAME = 'remark');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE purchase_order_detail ADD COLUMN remark VARCHAR(200) DEFAULT '''' COMMENT ''备注（如：参考适合车型）'' AFTER cost_price',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------
-- 1.2) 销售明细增加备注列（打印在送货单的「备注」列）
-- ---------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'sale_order_detail'
                      AND COLUMN_NAME = 'remark');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE sale_order_detail ADD COLUMN remark VARCHAR(200) DEFAULT '''' COMMENT ''备注（打印在送货单的备注列）'' AFTER sale_price',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------
-- 2) 修复历史数据中为 NULL 的创建/更新时间
--    旧版本实体写了 @TableField(fill=...) 但没有 MetaObjectHandler，
--    导致 create_time / update_time 被显式写成 NULL。
--    仅更新为 NULL 的行，可重复执行；历史行无法还原真实时间，这里用当前时间补齐
--    （列表排序已改为按 id 倒序，不依赖该字段）。
-- ---------------------------------------------
UPDATE product                SET create_time = IFNULL(create_time, NOW()), update_time = IFNULL(update_time, NOW()) WHERE create_time IS NULL OR update_time IS NULL;
UPDATE customer               SET create_time = IFNULL(create_time, NOW()), update_time = IFNULL(update_time, NOW()) WHERE create_time IS NULL OR update_time IS NULL;
UPDATE supplier               SET create_time = IFNULL(create_time, NOW()), update_time = IFNULL(update_time, NOW()) WHERE create_time IS NULL OR update_time IS NULL;
UPDATE purchase_order         SET create_time = IFNULL(create_time, NOW()), update_time = IFNULL(update_time, NOW()) WHERE create_time IS NULL OR update_time IS NULL;
UPDATE purchase_order_detail  SET create_time = IFNULL(create_time, NOW()), update_time = IFNULL(update_time, NOW()) WHERE create_time IS NULL OR update_time IS NULL;
UPDATE sale_order             SET create_time = IFNULL(create_time, NOW()), update_time = IFNULL(update_time, NOW()) WHERE create_time IS NULL OR update_time IS NULL;
UPDATE sale_order_detail      SET create_time = IFNULL(create_time, NOW()), update_time = IFNULL(update_time, NOW()) WHERE create_time IS NULL OR update_time IS NULL;
UPDATE inventory_log          SET create_time = IFNULL(create_time, NOW()) WHERE create_time IS NULL;
UPDATE inventory_check        SET create_time = IFNULL(create_time, NOW()), update_time = IFNULL(update_time, NOW()) WHERE create_time IS NULL OR update_time IS NULL;
UPDATE inventory_check_detail SET create_time = IFNULL(create_time, NOW()) WHERE create_time IS NULL;
UPDATE sys_user               SET create_time = IFNULL(create_time, NOW()) WHERE create_time IS NULL;

-- ---------------------------------------------
-- 3) 系统配置表（送货单抬头等参数改为界面维护）
--    已有数据不会受影响；重复执行不会报错。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS sys_config (
    config_key VARCHAR(50) NOT NULL COMMENT '配置项',
    config_value VARCHAR(500) DEFAULT '' COMMENT '配置值',
    remark VARCHAR(200) DEFAULT '' COMMENT '说明',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- ---------------------------------------------
-- 4) 口令升级说明
--    sys_user.password 中已有的明文口令（如 admin/admin123）无需手工处理：
--    新版本在用户首次成功登录后会自动改写为 PBKDF2 散列值。
-- ---------------------------------------------

-- 自检（预期 0 行）
-- SELECT COUNT(*) FROM product WHERE create_time IS NULL OR update_time IS NULL;
-- 自检（预期 1 行，表示 version 字段已存在）
-- SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='product' AND COLUMN_NAME='version';

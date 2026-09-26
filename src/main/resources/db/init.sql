-- =============================================
-- 进销存管理系统 数据库初始化 / 升级脚本（安全版）
-- MySQL 8.0
-- =============================================
-- 【安全说明 · 必读】
-- 1. 本脚本**不含任何 DROP / TRUNCATE / DELETE 语句**，不会删除表，更不会清空数据。
-- 2. 全部建库建表语句均为 IF NOT EXISTS，初始化账号为 INSERT IGNORE，
--    因此本脚本**可以重复执行**：全新安装、已有数据的库升级，都用这一个脚本。
-- 3. 需要"推倒重建"时，请先自行备份，然后显式手工执行：
--        DROP DATABASE jxc_db;
--    再运行本脚本。删除动作必须由你确认，脚本不会替你做。
-- 4. 已有数据的库执行本脚本后，会自动补齐新增字段（如 product.version），
--    并修复历史数据中为 NULL 的创建/更新时间，不影响任何已有业务数据。
--
-- 执行方式（建议指定 utf8mb4，避免中文注释乱码）：
--   mysql --default-character-set=utf8mb4 -u root -p < init.sql
-- =============================================

CREATE DATABASE IF NOT EXISTS jxc_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE jxc_db;

-- =============================================
-- 1. 商品表
-- =============================================
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code VARCHAR(50) NOT NULL COMMENT '商品编码（唯一）',
    name VARCHAR(100) NOT NULL COMMENT '商品名称',
    category VARCHAR(50) DEFAULT '' COMMENT '商品分类',
    spec VARCHAR(100) DEFAULT '' COMMENT '规格型号',
    unit VARCHAR(20) DEFAULT '' COMMENT '单位',
    cost_price DECIMAL(10,2) DEFAULT 0.00 COMMENT '成本价',
    sale_price DECIMAL(10,2) DEFAULT 0.00 COMMENT '销售价',
    stock_quantity INT DEFAULT 0 COMMENT '当前库存数量（由入库/出库自动计算）',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- =============================================
-- 2. 客户表
-- =============================================
CREATE TABLE IF NOT EXISTS customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code VARCHAR(50) NOT NULL COMMENT '客户编码',
    name VARCHAR(100) NOT NULL COMMENT '客户名称',
    contact VARCHAR(50) DEFAULT '' COMMENT '联系人',
    phone VARCHAR(20) DEFAULT '' COMMENT '联系电话',
    address VARCHAR(200) DEFAULT '' COMMENT '地址',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';

-- =============================================
-- 3. 供应商表
-- =============================================
CREATE TABLE IF NOT EXISTS supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code VARCHAR(50) NOT NULL COMMENT '供应商编码',
    name VARCHAR(100) NOT NULL COMMENT '供应商名称',
    contact VARCHAR(50) DEFAULT '' COMMENT '联系人',
    phone VARCHAR(20) DEFAULT '' COMMENT '联系电话',
    address VARCHAR(200) DEFAULT '' COMMENT '地址',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

-- =============================================
-- 4. 采购订单主表
-- =============================================
CREATE TABLE IF NOT EXISTS purchase_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_no VARCHAR(50) NOT NULL COMMENT '订单编号（CG+日期+序号）',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    purchase_date DATE NOT NULL COMMENT '采购日期',
    status VARCHAR(20) NOT NULL DEFAULT '待入库' COMMENT '订单状态：待入库/部分入库/已入库/已取消',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_supplier_id (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单主表';

-- =============================================
-- 5. 采购订单明细表
-- =============================================
CREATE TABLE IF NOT EXISTS purchase_order_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    quantity INT NOT NULL COMMENT '订单数量',
    received_quantity INT DEFAULT 0 COMMENT '已入库数量',
    cost_price DECIMAL(10,2) NOT NULL COMMENT '成本价',
    remark VARCHAR(200) DEFAULT '' COMMENT '备注（如：参考适合车型）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单明细表';

-- =============================================
-- 6. 销售订单主表
-- =============================================
CREATE TABLE IF NOT EXISTS sale_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_no VARCHAR(50) NOT NULL COMMENT '订单编号（XS+日期+序号）',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    sale_date DATE NOT NULL COMMENT '销售日期',
    status VARCHAR(20) NOT NULL DEFAULT '待出库' COMMENT '订单状态：待出库/部分出库/已出库/已取消',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单主表';

-- =============================================
-- 7. 销售订单明细表
-- =============================================
CREATE TABLE IF NOT EXISTS sale_order_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    quantity INT NOT NULL COMMENT '订单数量',
    shipped_quantity INT DEFAULT 0 COMMENT '已出库数量',
    sale_price DECIMAL(10,2) NOT NULL COMMENT '销售价',
    remark VARCHAR(200) DEFAULT '' COMMENT '备注（打印在送货单的备注列）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单明细表';

-- =============================================
-- 8. 库存流水表
-- =============================================
CREATE TABLE IF NOT EXISTS inventory_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    change_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变动时间',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    change_type VARCHAR(20) NOT NULL COMMENT '变动类型：入库/出库',
    change_quantity INT NOT NULL COMMENT '变动数量',
    after_stock INT NOT NULL COMMENT '变动后库存',
    related_order_no VARCHAR(50) DEFAULT '' COMMENT '关联单号',
    remark VARCHAR(200) DEFAULT '' COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_product_id (product_id),
    KEY idx_change_time (change_time),
    KEY idx_change_type (change_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水表';

-- =============================================
-- 9. 盘点单主表
-- =============================================
CREATE TABLE IF NOT EXISTS inventory_check (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    check_no VARCHAR(50) NOT NULL COMMENT '盘点单号',
    check_date DATE NOT NULL COMMENT '盘点日期',
    remark VARCHAR(200) DEFAULT '' COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点单主表';

-- =============================================
-- 10. 盘点单明细表
-- =============================================
CREATE TABLE IF NOT EXISTS inventory_check_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    check_id BIGINT NOT NULL COMMENT '盘点单ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    system_quantity INT NOT NULL COMMENT '系统库存数量',
    actual_quantity INT NOT NULL COMMENT '实盘数量',
    diff_quantity INT NOT NULL COMMENT '盈亏数量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_check_id (check_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点单明细表';

-- =============================================
-- 11. 系统用户表
-- =============================================
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码（PBKDF2 散列；兼容历史明文，登录成功后自动升级）',
    real_name VARCHAR(50) DEFAULT '' COMMENT '真实姓名',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- =============================================
-- 12. 系统配置表（送货单抬头等可在界面维护的参数）
-- =============================================
CREATE TABLE IF NOT EXISTS sys_config (
    config_key VARCHAR(50) NOT NULL COMMENT '配置项',
    config_value VARCHAR(500) DEFAULT '' COMMENT '配置值',
    remark VARCHAR(200) DEFAULT '' COMMENT '说明',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- =============================================
-- 13. 兼容旧库：补齐后续版本新增的字段（幂等，已存在则跳过）
--     说明：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，
--           因此用 information_schema 判断后再动态执行。
-- =============================================
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

-- =============================================
-- 14. 兼容旧库：修复历史数据中为 NULL 的创建/更新时间
--     旧版本因缺少 MetaObjectHandler，会把这两个字段写成 NULL；
--     这里仅更新为 NULL 的行，可重复执行，不影响正常数据。
--     注意：历史行无法还原真实时间，这里用当前时间补齐。
-- =============================================
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

-- =============================================
-- 15. 默认管理员账号：admin / admin123
--     口令已按 PBKDF2-HMAC-SHA256 散列存储，不落明文；
--     使用 INSERT IGNORE，重复执行不会覆盖你已修改过的密码。
-- =============================================
INSERT IGNORE INTO sys_user (username, password, real_name)
VALUES ('admin', 'pbkdf2$12000$X2hWqGr8/ayh7liCty6pdg==$aNm7vx8crRVfgRG4LL4GRvX6iQ5smocKbJitd0UettA=', '系统管理员');

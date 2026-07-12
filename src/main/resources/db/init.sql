-- =============================================
-- 进销存管理系统 数据库初始化脚本
-- MySQL 8.0
-- =============================================

CREATE DATABASE IF NOT EXISTS jxc_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE jxc_db;

-- =============================================
-- 1. 商品表
-- =============================================
DROP TABLE IF EXISTS product;
CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code VARCHAR(50) NOT NULL COMMENT '商品编码（唯一）',
    name VARCHAR(100) NOT NULL COMMENT '商品名称',
    category VARCHAR(50) DEFAULT '' COMMENT '商品分类',
    spec VARCHAR(100) DEFAULT '' COMMENT '规格型号',
    unit VARCHAR(20) DEFAULT '' COMMENT '单位',
    cost_price DECIMAL(10,2) DEFAULT 0.00 COMMENT '成本价',
    sale_price DECIMAL(10,2) DEFAULT 0.00 COMMENT '销售价',
    stock_quantity INT DEFAULT 0 COMMENT '当前库存数量（由入库/出库自动计算）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- =============================================
-- 2. 客户表
-- =============================================
DROP TABLE IF EXISTS customer;
CREATE TABLE customer (
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
DROP TABLE IF EXISTS supplier;
CREATE TABLE supplier (
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
DROP TABLE IF EXISTS purchase_order;
CREATE TABLE purchase_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_no VARCHAR(50) NOT NULL COMMENT '订单编号（CG+日期+序号）',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    purchase_date DATE NOT NULL COMMENT '采购日期',
    status VARCHAR(20) NOT NULL DEFAULT '待入库' COMMENT '订单状态：待入库/已入库/已取消',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_supplier_id (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单主表';

-- =============================================
-- 5. 采购订单明细表
-- =============================================
DROP TABLE IF EXISTS purchase_order_detail;
CREATE TABLE purchase_order_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    quantity INT NOT NULL COMMENT '订单数量',
    received_quantity INT DEFAULT 0 COMMENT '已入库数量',
    cost_price DECIMAL(10,2) NOT NULL COMMENT '成本价',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单明细表';

-- =============================================
-- 6. 销售订单主表
-- =============================================
DROP TABLE IF EXISTS sale_order;
CREATE TABLE sale_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_no VARCHAR(50) NOT NULL COMMENT '订单编号（XS+日期+序号）',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    sale_date DATE NOT NULL COMMENT '销售日期',
    status VARCHAR(20) NOT NULL DEFAULT '待出库' COMMENT '订单状态：待出库/已出库/已取消',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单主表';

-- =============================================
-- 7. 销售订单明细表
-- =============================================
DROP TABLE IF EXISTS sale_order_detail;
CREATE TABLE sale_order_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    quantity INT NOT NULL COMMENT '订单数量',
    shipped_quantity INT DEFAULT 0 COMMENT '已出库数量',
    sale_price DECIMAL(10,2) NOT NULL COMMENT '销售价',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单明细表';

-- =============================================
-- 8. 库存流水表
-- =============================================
DROP TABLE IF EXISTS inventory_log;
CREATE TABLE inventory_log (
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
DROP TABLE IF EXISTS inventory_check;
CREATE TABLE inventory_check (
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
DROP TABLE IF EXISTS inventory_check_detail;
CREATE TABLE inventory_check_detail (
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
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    real_name VARCHAR(50) DEFAULT '' COMMENT '真实姓名',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 默认管理员账户 admin / admin123
INSERT INTO sys_user (username, password, real_name) VALUES ('admin', 'admin123', '系统管理员');

-- ============================================
-- TDM 系统数据库初始化脚本
-- 创建用户表并插入初始管理员账号
-- ============================================

USE test_data;

-- 创建用户表
CREATE TABLE IF NOT EXISTS `user` (
                                      `uid`  VARCHAR(32)  NOT NULL COMMENT '用户唯一标识',
    `name` VARCHAR(64)  NOT NULL COMMENT '用户姓名',
    `pwd`  VARCHAR(128) NOT NULL COMMENT 'BCrypt加密密码',
    `type` VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '账户类型: USER/ADMIN/DEVELOPER',
    PRIMARY KEY (`uid`),
    INDEX `idx_type` (`type`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 插入初始管理员账号
-- 用户名: 管理员  账号: UADMIN00001  类型: DEVELOPER
INSERT INTO `user` (`uid`, `name`, `pwd`, `type`)
VALUES ('UADMIN00001', '管理员', '$2a$10$65M/6FviDqGAZ6HKG2BzMOVOQGdecVl91t19mXsjT0hfWG.X6/Dda', 'DEVELOPER')
    ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `pwd` = VALUES(`pwd`), `type` = VALUES(`type`);
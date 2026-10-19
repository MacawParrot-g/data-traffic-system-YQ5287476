package org.example.service;

import org.example.common.Result;

/**
 * MySQL 运行状态监控与全量备份服务。
 * 监控通过定期执行 SHOW GLOBAL STATUS / SHOW VARIABLES 采集连接数、运行线程、慢查询等指标；
 * 备份通过 mysqldump 对生产库（默认数据源）做全量逻辑备份，滚动保留最近若干版本。
 */
public interface MySQLBackupMonitorService {

    /** 采集 MySQL 运行状态指标 */
    Result getMonitorStatus();

    /** 手动触发一次全量备份 */
    Result manualBackup();

    /** 查询备份目录下已有的备份文件列表 */
    Result listBackups();

    /** 定时任务：按配置的 cron 自动执行全量备份 */
    void scheduledBackup();
}
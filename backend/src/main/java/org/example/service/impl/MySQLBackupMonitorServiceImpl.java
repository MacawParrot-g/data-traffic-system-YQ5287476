package org.example.service.impl;

import org.example.common.Result;
import org.example.service.MySQLBackupMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class MySQLBackupMonitorServiceImpl implements MySQLBackupMonitorService {

    private static final Logger log = LoggerFactory.getLogger(MySQLBackupMonitorServiceImpl.class);

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String BACKUP_PREFIX = "backup_";
    private static final String BACKUP_SUFFIX = ".sql";

    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUser;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${backup.dir:./backup}")
    private String backupDir;

    @Value("${backup.retain:3}")
    private int retainCount;

    public MySQLBackupMonitorServiceImpl(@Qualifier("defaultDataSource") DataSource defaultDataSource) {
        this.jdbcTemplate = new JdbcTemplate(defaultDataSource);
    }

    // ==================== 监控 ====================

    @Override
    public Result getMonitorStatus() {
        try {
            ensureBackupDir();

            long threadsConnected = readStatusNumber("Threads_connected");
            long threadsRunning = readStatusNumber("Threads_running");
            long maxConnections = readVariableNumber("max_connections");

            double usagePercent = maxConnections > 0
                    ? Math.round(threadsConnected * 10000.0 / maxConnections) / 100.0
                    : 0.0;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("threadsConnected", threadsConnected);
            data.put("threadsRunning", threadsRunning);
            data.put("maxConnections", maxConnections);
            data.put("usagePercent", usagePercent);
            data.put("slowQueryLog", readVariable("slow_query_log"));
            data.put("slowQueryLogFile", readVariable("slow_query_log_file"));
            data.put("longQueryTime", readVariable("long_query_time"));
            data.put("db", parseDatabase(datasourceUrl));
            data.put("host", parseHost(datasourceUrl));
            data.put("port", parsePort(datasourceUrl));
            data.put("backupDir", new File(backupDir).getAbsolutePath());
            data.put("retainCount", retainCount);
            data.put("backupCount", listBackupFiles().size());

            return Result.success("监控信息获取成功", data);
        } catch (Exception e) {
            log.error("MySQL监控信息获取失败: {}", e.getMessage());
            return Result.fail("监控信息获取失败：" + e.getMessage());
        }
    }

    private long readStatusNumber(String variable) {
        String value = readStatus(variable);
        try {
            return value == null ? 0L : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private long readVariableNumber(String variable) {
        String value = readVariable(variable);
        try {
            return value == null ? 0L : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String readStatus(String variable) {
        // variable 为内部固定常量，无注入风险
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW GLOBAL STATUS LIKE '" + variable + "'");
        return extractValue(rows);
    }

    private String readVariable(String variable) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW VARIABLES LIKE '" + variable + "'");
        return extractValue(rows);
    }

    private String extractValue(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        Object value = row.get("Value");
        if (value == null) {
            value = row.values().stream().skip(1).findFirst().orElse(null);
        }
        return value == null ? null : value.toString();
    }

    // ==================== 备份 ====================

    @Override
    public Result manualBackup() {
        return doBackup("手动");
    }

    @Scheduled(cron = "${backup.cron:0 0 3 */3 * ?}")
    @Override
    public void scheduledBackup() {
        try {
            Result result = doBackup("定时");
            if (!result.isSuccess()) {
                log.warn("定时备份失败: {}", result.getMessage());
            }
        } catch (Exception e) {
            log.error("定时备份异常: {}", e.getMessage());
        }
    }

    private synchronized Result doBackup(String trigger) {
        try {
            ensureBackupDir();

            String timestamp = LocalDateTime.now().format(TS_FORMAT);
            String fileName = BACKUP_PREFIX + parseDatabase(datasourceUrl) + "_" + timestamp + BACKUP_SUFFIX;
            Path target = Paths.get(backupDir, fileName);

            String mysqldump = resolveMysqldump();
            List<String> command = new ArrayList<>();
            command.add(mysqldump);
            command.add("--host=" + parseHost(datasourceUrl));
            command.add("--port=" + parsePort(datasourceUrl));
            command.add("--user=" + datasourceUser);
            command.add("--single-transaction");
            command.add("--routines");
            command.add("--triggers");
            command.add("--events");
            command.add("--databases");
            command.add(parseDatabase(datasourceUrl));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("MYSQL_PWD", datasourcePassword == null ? "" : datasourcePassword);
            pb.redirectErrorStream(false);
            pb.redirectOutput(target.toFile());

            long startTime = System.currentTimeMillis();
            Process process = pb.start();

            String stderr;
            try (BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = errReader.readLine()) != null) {
                    sb.append(line).append(System.lineSeparator());
                }
                stderr = sb.toString();
            }

            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                deleteQuietly(target);
                return Result.fail("备份超时（超过30分钟），已终止并清理临时文件");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                deleteQuietly(target);
                log.error("mysqldump 执行失败 exitCode={} stderr={}", exitCode, stderr);
                return Result.fail("备份失败：" + (stderr.isEmpty() ? "mysqldump 退出码 " + exitCode : stderr.trim()));
            }

            long size = Files.exists(target) ? Files.size(target) : 0L;
            int deleted = pruneOldBackups();
            long elapsed = System.currentTimeMillis() - startTime;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fileName", fileName);
            data.put("size", size);
            data.put("sizeText", humanSize(size));
            data.put("elapsedMs", elapsed);
            data.put("deletedCount", deleted);

            String message = trigger + "备份成功：" + fileName + "（" + humanSize(size) + "）"
                    + (deleted > 0 ? "，已清理最旧备份 " + deleted + " 个" : "");
            log.info(message);
            return Result.success(message, data);
        } catch (Exception e) {
            log.error("备份异常: {}", e.getMessage());
            return Result.fail("备份异常：" + e.getMessage());
        }
    }

    /** 滚动保留最近 retainCount 个备份，超出则从最旧的开始删除，返回删除数量 */
    private int pruneOldBackups() {
        List<File> files = listBackupFiles();
        int deleted = 0;
        // files 已按时间戳升序排列（最旧在前），保留最新的 retainCount 个
        int removeCount = files.size() - retainCount;
        for (int i = 0; i < files.size() && removeCount > 0; i++) {
            if (files.get(i).delete()) {
                deleted++;
                removeCount--;
                log.info("清理旧备份: {}", files.get(i).getName());
            }
        }
        return deleted;
    }

    @Override
    public Result listBackups() {
        try {
            ensureBackupDir();
            List<File> files = listBackupFiles();
            List<Map<String, Object>> backups = new ArrayList<>();
            long totalSize = 0L;
            for (File f : files) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("fileName", f.getName());
                info.put("size", f.length());
                info.put("sizeText", humanSize(f.length()));
                info.put("time", LocalDateTime.ofInstant(
                        java.nio.file.attribute.FileTime.fromMillis(f.lastModified()).toInstant(),
                        ZoneId.systemDefault()).format(DISPLAY_FORMAT));
                backups.add(info);
                totalSize += f.length();
            }
            // 前端展示最新的排在前面
            backups.sort((a, b) -> String.valueOf(b.get("fileName")).compareTo(String.valueOf(a.get("fileName"))));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("backupDir", new File(backupDir).getAbsolutePath());
            data.put("retainCount", retainCount);
            data.put("count", backups.size());
            data.put("totalSize", totalSize);
            data.put("totalSizeText", humanSize(totalSize));
            data.put("backups", backups);
            return Result.success("备份列表获取成功", data);
        } catch (Exception e) {
            return Result.fail("备份列表获取失败：" + e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    private List<File> listBackupFiles() {
        File dir = new File(backupDir);
        File[] arr = dir.listFiles((d, name) -> name.startsWith(BACKUP_PREFIX) && name.endsWith(BACKUP_SUFFIX));
        List<File> files = arr == null ? new ArrayList<>() : new ArrayList<>(List.of(arr));
        // 文件名内嵌时间戳，字典序即时间序，升序=最旧在前
        files.sort(Comparator.comparing(File::getName));
        return files;
    }

    private void ensureBackupDir() throws Exception {
        Path path = Paths.get(backupDir);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log.info("备份目录不存在，已自动创建: {}", path.toAbsolutePath());
        }
    }

    private String resolveMysqldump() {
        String configured = System.getenv("MYSQLDUMP_BIN");
        return (configured != null && !configured.isBlank()) ? configured : "mysqldump";
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    // jdbc:mysql://host:port/database?params
    private String parseHost(String url) {
        return stripQueryAndSlash(url).replaceAll("^jdbc:mysql://", "").split(":")[0].split("/")[0];
    }

    private String parsePort(String url) {
        String body = stripQueryAndSlash(url).replaceAll("^jdbc:mysql://", "");
        if (body.contains(":")) {
            String port = body.substring(body.indexOf(":") + 1);
            port = port.split("/")[0];
            if (port.matches("\\d+")) return port;
        }
        return "3306";
    }

    private String parseDatabase(String url) {
        String body = stripQueryAndSlash(url).replaceAll("^jdbc:mysql://", "");
        body = body.replaceAll("^[^/]*/", "");
        return body.split("/")[0];
    }

    private String stripQueryAndSlash(String url) {
        String u = url;
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        return u;
    }
}

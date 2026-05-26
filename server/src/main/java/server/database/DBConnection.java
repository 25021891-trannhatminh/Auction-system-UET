package server.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DBConnection {

    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());
    private static HikariDataSource dataSource;

    static {
        try (InputStream is = DBConnection.class.getClassLoader()
            .getResourceAsStream("db.properties")) {

            if (is == null) {
                LOGGER.severe("Không tìm thấy file db.properties trong resources");
            } else {
                Properties props = new Properties();
                props.load(is);

                HikariConfig config = new HikariConfig();
                config.setDriverClassName(props.getProperty("db.driver"));
                config.setJdbcUrl(props.getProperty("db.url"));
                config.setUsername(props.getProperty("db.user"));
                config.setPassword(props.getProperty("db.password"));

                // TiDB Cloud — 400 connections limit, không còn bị giới hạn như filess
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(30000);
                config.setIdleTimeout(600000);

                // TiDB Cloud tự đóng connection idle quá 5 phút trên public endpoint
                // → keepalive ping mỗi 2 phút để giữ connection sống
                config.setKeepaliveTime(120000);

                // Recycle connection sau 10 phút, tránh stale connection
                config.setMaxLifetime(600000);

                // Test connection trước khi lấy từ pool
                config.setConnectionTestQuery("SELECT 1");

                // Giữ NOW(), TIMESTAMPDIFF và DATETIME trả về DB khớp múi giờ app Việt Nam.
                config.setConnectionInitSql("SET time_zone = '+07:00'");

                dataSource = new HikariDataSource(config);
                LOGGER.info("Load DB config thành công");

                // Đóng pool khi JVM tắt → tránh leak connection
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (dataSource != null && !dataSource.isClosed()) {
                        dataSource.close();
                        LOGGER.info("HikariCP pool đã đóng");
                    }
                }));
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi khởi tạo DBConnection", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        try (Connection conn = getConnection()) {
            if (conn != null) {
                LOGGER.info("Kết nối Database thành công");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Kết nối Database thất bại", e);
        }
    }
}
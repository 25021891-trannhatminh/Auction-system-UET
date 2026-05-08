package server.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class quản lý kết nối Database.
 *
 * - Load cấu hình từ db.properties
 * - Tạo Connection cho DAO sử dụng
 */
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
                config.setMaximumPoolSize(4);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(30000);
                config.setIdleTimeout(600000);

                dataSource = new HikariDataSource(config);
                LOGGER.info("Load DB config thành công");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi khởi tạo DBConnection", e);
        }
    }

    /**
     * Lấy connection tới database.
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // Test nhanh
    public static void main(String[] args) {
        try (Connection conn = getConnection()) {
            if (conn != null) {
                LOGGER.info("Kết nối Database thành công");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Kết nối Database thất bại", e);
        }
    }
}
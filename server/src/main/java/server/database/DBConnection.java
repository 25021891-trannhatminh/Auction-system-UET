package server.database;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
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
    private static final Properties props = new Properties();

    static {
        try (InputStream is = DBConnection.class.getClassLoader()
            .getResourceAsStream("db.properties")) {

            if (is == null) {
                LOGGER.severe("Không tìm thấy file db.properties trong resources");
            } else {
                props.load(is);
                Class.forName(props.getProperty("db.driver"));
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
        return DriverManager.getConnection(
            props.getProperty("db.url"),
            props.getProperty("db.user"),
            props.getProperty("db.password")
        );
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
package server.database;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnection {
    private static Properties props = new Properties();

    static {
        // Tự động chạy khi class được nạp vào bộ nhớ
        try (InputStream is = DBConnection.class.getClassLoader()
                .getResourceAsStream("db.properties")) {

            if (is == null) {
                // Nếu không tìm thấy file thật, báo lỗi ngay để bạn đi tạo file
                System.err.println("❌ LỖI: Không tìm thấy file 'db.properties' trong thư mục resources!");
            } else {
                props.load(is);
                // Nạp Driver
                Class.forName(props.getProperty("db.driver"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                props.getProperty("db.url"),
                props.getProperty("db.user"),
                props.getProperty("db.password")
        );
    }

    public static void main(String[] args) {
        try {
            Connection conn = getConnection();
            if (conn != null) {
                System.out.println("✅ Kết nối Database thành công!");
                conn.close();
            }
        } catch (SQLException e) {
            System.err.println("❌ Kết nối thất bại: " + e.getMessage());
        }
    }
}
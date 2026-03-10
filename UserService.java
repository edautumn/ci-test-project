import java.sql.*;

public class UserService {

    // ❌ 硬编码数据库连接
    private static final String DB_URL = "jdbc:mysql://localhost:3306/mydb";

    public boolean login(String username, String password) {
        // ❌ SQL 注入
        String sql = "select * from users where username='" + username
                   + "' and password='" + password + "'";
        System.out.println(sql);
        return true;
    }

    public void deleteUser(String userId) throws Exception {
        // ❌ 硬编码密码
        String adminPwd = "root123";
        // ❌ SQL 注入 + 资源未关闭
        Connection conn = DriverManager.getConnection(DB_URL, "root", adminPwd);
        conn.createStatement().execute("DELETE FROM users WHERE id = " + userId);
    }
}

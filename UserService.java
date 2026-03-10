import java.sql.Connection;
import java.sql.DriverManager;

public class UserService {

    public boolean login(String username,String password){

        String sql = "select * from users where username='"+username+"' and password='"+password+"'";

        System.out.println(sql);

        return true;

    }
    // 加到 UserService.java 里
    public void deleteUser(String userId) throws Exception {
        // ❌ 高危：直接拼接 SQL + 硬编码密码
        String adminPwd = "root123";
        Connection conn = DriverManager.getConnection(DB_URL, "root", adminPwd);
        conn.createStatement().execute("DELETE FROM users WHERE id = " + userId);
    }
}
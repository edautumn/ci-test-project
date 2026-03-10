public class UserService {

    public boolean login(String username,String password){

        String sql = "select * from users where username='"+username+"' and password='"+password+"'";

        System.out.println(sql);

        return true;

    }

}
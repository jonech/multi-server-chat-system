package au.edu.unimelb.comp90015_chjq.client;

/**
 * Created by alw on 2016/10/13.
 */
public class AccountInfo {
    private String account;
    private String password;
    private String accessToken;

    public AccountInfo(String account, String password, String accessToken){
        this.account = account;
        this.password = password;
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getPassword() {
        return password;
    }

    public String getAccount() {
        return account;
    }
}

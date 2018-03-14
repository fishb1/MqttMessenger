package fb.ru.mqtttest.rest;

/**
 * Запрос к серверу на вход.
 *
 * Created by kolyan on 13.03.18.
 */
public class LoginRequestBody {

    public final String username;
    public final String password;

    public LoginRequestBody(String u, String p) {
        username = u;
        password = p;
    }
}

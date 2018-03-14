package fb.ru.mqtttest.rest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Интерфейс рест-адаптера для ретрофита. Исп. только для логина
 *
 * Created by kolyan on 13.03.18.
 */
public interface ApiService {

    @POST("user/create/v1")
    Call<Void> login(@Body LoginRequestBody request);
}

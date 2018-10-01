package fb.ru.mqtttest.rest;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Интерфейс рест-адаптера для ретрофита. Исп. только для логина
 *
 * Created by kolyan on 13.03.18.
 */
public interface ApiService {

    @GET("userManager/activatePin")
    Call<DeviceConfig> activatePin(@Query("pin") String pin);
}

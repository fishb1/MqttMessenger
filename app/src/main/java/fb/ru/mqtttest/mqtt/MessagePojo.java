package fb.ru.mqtttest.mqtt;

import java.util.Date;

public class MessagePojo {

    private long id;
    private Date date;
    private String payload;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessagePojo that = (MessagePojo) o;
        return id == that.id && (date != null ? date.equals(that.date) : that.date == null)
                && (payload != null ? payload.equals(that.payload) : that.payload == null);
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (date != null ? date.hashCode() : 0);
        result = 31 * result + (payload != null ? payload.hashCode() : 0);
        return result;
    }
}

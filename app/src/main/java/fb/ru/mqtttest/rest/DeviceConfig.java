package fb.ru.mqtttest.rest;

public class DeviceConfig {

    public User user;
    public MqttConfig mqttUser;

/*

{
  "_id": "5bb2423d76731400186f979c",
  "type": "5b5ab66414063a4778c5bc71",
  "model": "default",
  "user": {
    "devices": [
      "5bb2421976731400186f9797",
      "5bb2423d76731400186f979c"
    ],
    "_id": "5bb2421976731400186f9795",
    "username": "kolyan",
    "password": "a1234",
    "phone": 79883865559,
    "__v": 2
  },
  "pin": "",
  "isActive": true,
  "isParent": false,
  "mqttUser": {
    "publish_acl": [
      {
        "pattern": "mv1/5bb2423d76731400186f979c/#"
      }
    ],
    "subscribe_acl": [
      {
        "pattern": "mv1/5bb2423d76731400186f979c/#"
      }
    ],
    "_id": "5bb2423d76731400186f979e",
    "mountpoint": "",
    "client_id": "153840902132933",
    "passhash": "$2a$10$cXk9VMJpooVFa4aHAC56tuOZbigAunWz2Zg3VPzwFyDLHp1vOKGIO",
    "username": "5bb2423d76731400186f979c",
    "__v": 0
  },
  "__v": 0
}


 */

    public static class User {

        public String username;
        public String password;
        public String phone;
        public String[] devices;
    }

    public static class MqttConfig {

        public String client_id;
        public String username;
        public String passhash;
    }
}

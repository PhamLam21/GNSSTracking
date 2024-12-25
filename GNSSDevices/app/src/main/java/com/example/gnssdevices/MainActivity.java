package com.example.gnssdevices;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.util.Log;
import android.util.Base64;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.api.IMapController;
import org.osmdroid.views.overlay.Marker;
import org.json.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

public class MainActivity extends AppCompatActivity {
    private Marker marker;
    private IMapController mapController;
    private final BlockingQueue<String> sfrbxQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<byte[]> gnssQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<byte[]> navQueue = new LinkedBlockingQueue<>();
    private final NMEAHandler nmeaPosition = new NMEAHandler();
    private final UartReader uartReader = new UartReader();
    private final OkHttpClient client = new OkHttpClient();
    private Switch switchGPS, switchBeidou, switchGalileo, switchGlonass;
    private CheckBox appCheck, ubloxCheck;
    private final GNSSControl gnssControl = new GNSSControl();
    private Thread receiveThread, nmeaThread, sfrbxThread, ubloxDetect;
    private final String publicKey = "MIIBCgKCAQEAuqvwyKfMPcEkSElMM59pBNFLJLIAqJYWdHe6w7oaHf9sPNTQ3g+/E9dUuZH8TWqimPr5Wq/2pDmD8D4wnXeNe0" +
            "9ldsPFxGMrLxdHEscin56+SAVoX1O0bumSUIKiODHLTNkxAIibZkUbPSJZDySRLAoQ+21e9JL6/ocRMN21W37CF/HVPBB5JPLIOgo2zqg3VX9DUIKQG72Wh8b" +
            "6TGMwDE4FIQQXcsTA1UuCVEC41B0FQnygA6IdK11TTart5WMFRhWufcI/yZL7MF+/4myob5m5ESa4oQWHT7twHOjpfo7uJRF9PaB7lRMWQH5sEnQqBdjNUicF" +
            "pTPR0D7XxKmLDQIDAQAB";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        //Khởi tạo và mở cổng UART
        if (uartReader.openUart("/dev/ttyHSL0")) {
            Log.d("UART", "Kết nối thành công với UART");
        } else {
            Log.e("UART", "Không thể mở UART");
        }

        //Tạo bản đồ
        MapView map;
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        mapController = map.getController();
        mapController.setZoom(18.0);
        marker = new Marker(map);
        map.getOverlays().add(marker);

        //switch, checkbox
        ubloxCheck = findViewById(R.id.checkBox1);
        appCheck = findViewById(R.id.checkBox2);
        switchGPS = findViewById(R.id.switch1);
        switchGalileo = findViewById(R.id.switch2);
        switchGlonass = findViewById(R.id.switch3);
        switchBeidou = findViewById(R.id.switch4);
        switchGPS.setOnCheckedChangeListener(this::toggleGPS);
        switchBeidou.setOnCheckedChangeListener(this::toggleBeidou);
        switchGalileo.setOnCheckedChangeListener(this::toggleGalileo);
        switchGlonass.setOnCheckedChangeListener(this::toggleGlonass);

        //Các thread xử lý
        recieveDataUart();
        processSFRBX();
        processNMEA();
        ubloxDetectSpoofing();

        //Lay thong tin switch
        getSwitchStatus();
    }

    private void getSwitchStatus() {
        uartReader.write(gnssPollMessage());
        try {
            // Lấy ban tin UBX-CFG-GNSS
            byte[] gnssMess = gnssQueue.take();
            GNSSParser gnssParser = new GNSSParser(gnssMess);
            switchGPS.setChecked(gnssParser.isGpsEnabled());
            switchGalileo.setChecked(gnssParser.isGalileoEnabled());
            switchBeidou.setChecked(gnssParser.isBeidouEnabled());
            switchGlonass.setChecked(gnssParser.isGlonassEnabled());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void recieveDataUart() {
        receiveThread = new Thread(() -> {
            GNSSProcessor gnssProcessor = new GNSSProcessor();
            NMEAProcessor nmeaProcessor = new NMEAProcessor();
            SFRBXProcessor sfrbxProcessor = new SFRBXProcessor();
            NavStatusProcessor navStatusProcessor = new NavStatusProcessor();
            while (true) {
                try {
                    int numRead = uartReader.read();
                    if(numRead >= 0)
                    {
                        try {
                            byte[] gnssMessage = gnssProcessor.processIncomingByte(numRead);
                            String nmeaString = nmeaProcessor.processIncomingByte(numRead);
                            String sfrbxString = sfrbxProcessor.processIncomingByte(numRead);
                            byte[] navMess = navStatusProcessor.processIncomingByte(numRead);
                            if (gnssMessage != null) {
                                try {
                                    gnssQueue.put(gnssMessage);
                                    if(gnssQueue.size() >= 2)
                                    {
                                        gnssQueue.clear();
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            if(nmeaString != null) {
                                Log.d("NMEA", nmeaString);
                                if (nmeaString.startsWith("RMC", 3)) {
                                    try {
                                        nmeaPosition.parse(nmeaString);
                                    } catch (Exception e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            }
                            if(sfrbxString != null) {
                                String completeSFRBX = "\"" + sfrbxString + "\"";
                                Log.d("UBX", completeSFRBX);
                                try {
                                    sfrbxQueue.put(completeSFRBX);
                                    if(sfrbxQueue.size() > 8)
                                    {
                                        sfrbxQueue.clear();
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            if(navMess != null) {
                                try {
                                    navQueue.put(navMess);
                                    if(navQueue.size() > 3)
                                    {
                                        navQueue.clear();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        receiveThread.start();
    }

    private void processNMEA() {
        nmeaThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    //Cap nhat vi tri
                    GeoPoint geoPoint = new GeoPoint(nmeaPosition.position.lat, nmeaPosition.position.lon);
                    runOnUiThread(() -> {
                        try {
                            updateMapLocation(geoPoint);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    Thread.sleep(5000); //Moi 10s cap nhat 1 lan
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        nmeaThread.start();
    }

    private void processSFRBX() {
        sfrbxThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Lấy bản tin SFRBX từ hàng
                    long startTime = System.currentTimeMillis(); // Lấy thời gian bắt đầu
                    long endTime = startTime + 20000; // Chạy trong 10 giây (10000ms)
                    while(System.currentTimeMillis() < endTime) {
                        String sfrbxData = sfrbxQueue.take();

                        // Delay 1 giây trước khi gửi
                        Thread.sleep(1000);

                        // Gửi bản tin SFRBX lên server
                        sendToServer(sfrbxData);
                    }
                    //Sau 30s mới detech tiếp
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        sfrbxThread.start();
    }

    private void ubloxDetectSpoofing() {
        ubloxDetect = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    byte[] navMess = navQueue.take();
                    String navString = bytesToHex(navMess);
                    Log.d("NAV: ", navString);
                    int isSpoofing = extractSpoofDetState(navMess);
                    if(isSpoofing == 2 || isSpoofing == 3) {
                        runOnUiThread(() -> {
                            try {
                                ubloxCheck.setChecked(true);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            runOnUiThread(() -> ubloxCheck.setChecked(false)); // Bỏ dấu checkbox sau 10s
                        }, 10000);
                        Thread.sleep(10000); //10s detect 1 lan
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        ubloxDetect.start();
    }

    @SuppressLint("DefaultLocale")
    private void updateMapLocation(GeoPoint geoPoint) {
        if (geoPoint != null) {
            marker.setPosition(geoPoint);
            marker.setRotation(nmeaPosition.position.dir);
            marker.setTitle("Vận tốc: " + String.format("%.2f", nmeaPosition.position.velocity*1.852) + " km/h");
            mapController.setCenter(geoPoint);
        }
    }

    private void sendToServer(String ubxData) {
        MediaType MEDIA_TYPE_TEXT = MediaType.parse("application/json; charset=utf-8");

        Request request = new Request.Builder()
                .url("http://203.171.20.94:9201/api/NavigationModels/check")
                .post(RequestBody.create(ubxData, MEDIA_TYPE_TEXT))
                .build();
        try (Response response = client.newCall(request).execute()) {
//            Log.d("Http", Objects.requireNonNull(response.body()).string());
            String responseBody = Objects.requireNonNull(response.body()).string();
            Log.d("Http", responseBody);
            String message = parseMessage(responseBody);
            String signature = parseSignature(responseBody);
            if((message != null) && (signature != null)) {
//                if(verifySignature(message, signature, publicKey)) {
                    String[] messageData = message.split("-");
                    if(messageData[2].equals("NotFound")) {
                        runOnUiThread(() -> {
                            try {
                                appCheck.setChecked(true);
                                switch (messageData[1]) {
                                    case "0":
                                    case "1":
                                    case "5":
                                        switchGPS.setChecked(false);
                                        sendCommand(1); // Cập nhật GNSS
                                        break;
                                    case "2":
                                        switchGalileo.setChecked(false);
                                        sendCommand(3);
                                        break;
                                    case "3":
                                        switchBeidou.setChecked(false);
                                        sendCommand(4);
                                        break;
                                    case "6":
                                        switchGlonass.setChecked(false);
                                        sendCommand(6);
                                        break;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            runOnUiThread(() -> appCheck.setChecked(false)); // Bỏ dấu checkbox sau 10 giây
                        }, 10000);
                    }
//                } else {
//                    Log.d("Data: ", "Data đã bị thay đổi");
//                }
            }
        } catch (IOException e) {
            Thread.currentThread().interrupt();
        }
    }

    //Hàm bật/tắt GPS
    private void toggleGPS(CompoundButton buttonView, boolean isChecked) {
        sendCommand(1);
    }
    //Hàm bật/tắt Beidou
    private void toggleBeidou(CompoundButton buttonView, boolean isChecked) {
        sendCommand(4);
    }
    //Hàm bật/tắt Galileo
    private void toggleGalileo(CompoundButton buttonView, boolean isChecked) {
        sendCommand(3);
    }
    //Hàm bật/tắt GLONASS
    private void toggleGlonass(CompoundButton buttonView, boolean isChecked) {
        sendCommand(6);
    }

    //Lenh bat/tat hệ thống định vị
    private void sendCommand(int gnss) {
        boolean isGps = switchGPS.isChecked();
        boolean isGalileo = switchGalileo.isChecked();
        boolean isBeidou = switchBeidou.isChecked();
        boolean isGlonass = switchGlonass.isChecked();
        byte[] ubxMessage = gnssControl.createConfigGNSSMessage(isGps, isGps, isGalileo, isBeidou, isGps, isGlonass);
        byte[] saveConfig = saveConfigMessage();
        if(ubxMessage != null)
        {
            uartReader.write(ubxMessage);
            uartReader.write(saveConfig);
        } else {
            switch(gnss) {
                case 1:
                    switchGPS.setChecked(true);
                    break;
                case 3:
                    switchGalileo.setChecked(true);
                    break;
                case 4:
                    switchBeidou.setChecked(true);
                    break;
                case 6:
                    switchGlonass.setChecked(true);
                    break;
            }
        }
    }

    //Lenh poll UBX-CFG-GNSS
    private static byte[] gnssPollMessage() {
        return new byte[] {
                (byte) 0xB5, (byte) 0x62, (byte) 0x06, (byte) 0x3E,
                (byte) 0x00, (byte) 0x00, (byte) 0x44, (byte) 0xD2
        };
    }

    //Lenh luu cau hinh ublox
    private static byte[] saveConfigMessage() {
        return new byte[] {
                (byte) 0xB5, (byte) 0x62, (byte) 0x06, (byte) 0x09,
                (byte) 0x0D, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x1D,
                (byte) 0xAB
        };
    }

    private static boolean verifySignature(String data, String signedData, String publicKey) {
        try {
            //Chuyen doi khoa cong khai tu chuoi Base64
            byte[] publicKeyBytes = Base64.decode(publicKey, Base64.DEFAULT);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyBytes);
            PublicKey pubKey = keyFactory.generatePublic(spec);

            //Tao doi tuong signature voi thuat toan phu hop
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(pubKey);

            //Cung cap du lieu goc
            signature.update(data.getBytes());

            //Chuyen doi chu ky tu chuoi Base64
            byte[] signedBytes = Base64.decode(signedData, Base64.DEFAULT);

            //Xac minh chu ky
            return signature.verify(signedBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String parseMessage(String response) {
        try {
            // Chuyển chuỗi JSON thành JSONObject
            JSONObject jsonObject = new JSONObject(response);
            return jsonObject.getString("message");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String parseSignature(String response) {
        try {
            // Chuyển chuỗi JSON thành JSONObject
            JSONObject jsonObject = new JSONObject(response);
            return jsonObject.getString("signature");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private int extractSpoofDetState(byte[] message) {
        if (message[2] != (byte) 0x01 || message[3] != (byte) 0x03) {
            throw new IllegalArgumentException("Not a UBX-NAV-STATUS message");
        }

        // `flag2` is located at a specific offset in the payload
        int flag2Offset = 13; // Skip 8-byte header + 4-byte status data
        byte flag2 = message[flag2Offset];

        // Extract the `spoofDetState` (bits 4-6 in `flag2`)
        return (flag2 >> 3) & 0b00011;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            // Chuyển từng byte sang dạng hex, đảm bảo luôn có 2 ký tự (thêm '0' nếu cần)
            String hex = String.format("%02X", b);
            hexString.append(hex);
        }
        return hexString.toString();
    }

    protected void onDestroy() {
        super.onDestroy();
        uartReader.closeUart();
//        handler.removeCallbacks(updateOSM);
        if (receiveThread != null && receiveThread.isAlive()) {
            receiveThread.interrupt();
        }
        if (sfrbxThread != null && sfrbxThread.isAlive()) {
            sfrbxThread.interrupt();
        }
        if (nmeaThread != null && nmeaThread.isAlive()) {
            nmeaThread.interrupt();
        }
        if (ubloxDetect != null && ubloxDetect.isAlive()) {
            ubloxDetect.interrupt();
        }
    }
}

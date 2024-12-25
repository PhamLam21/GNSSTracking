package com.example.gnssdevices;

import android.util.Log;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class UartReader {

    private FileInputStream inputStream;
    private FileOutputStream outputStream;

    /**
     * Mở cổng UART
     * @param devicePath Đường dẫn đến thiết bị UART (VD: /dev/ttyHSL0)
     * @return true nếu mở thành công, false nếu thất bại
     */
    public boolean openUart(String devicePath) {
        try {
            inputStream = new FileInputStream(devicePath);
            outputStream = new FileOutputStream(devicePath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Đóng cổng UART
     */
    public void closeUart() {
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            Log.d("UartHandler", "Đã đóng cổng UART.");
        } catch (IOException e) {
            Log.e("UartHandler", "Lỗi khi đóng cổng UART: " + e.getMessage());
        }
    }

    /**
     * Đọc một byte từ UART
     * @return byte đã đọc hoặc -1 nếu không có dữ liệu
     */
    public int read() {
        try {
            int numRead = inputStream.read();
            if(numRead >= 0)
            {
                return numRead;
            }
        } catch (IOException e) {
            Log.e("UartHandler", "Lỗi khi đọc từ UART: " + e.getMessage());
            return -1;
        }
        return -1;
    }

    /**
     * Ghi dữ liệu vào UART
     * @param data Mảng byte cần ghi
     */
    public void write(byte[] data) {
        try {
            outputStream.write(data);
            outputStream.flush();
            Log.d("UartHandler", "Đã gửi dữ liệu thành công.");
        } catch (IOException e) {
            Log.e("UartHandler", "Lỗi khi ghi vào UART: " + e.getMessage());
        }
    }
}

package com.example.gnssdevices;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GNSSControl {

    /**
     * Tạo bản tin UBX-CFG-GNSS để cấu hình bật/tắt GNSS.
     *
     * @param gps      bật/tắt GPS
     * @param glonass  bật/tắt GLONASS
     * @param galileo  bật/tắt Galileo
     * @param beidou   bật/tắt BeiDou
     * @param qzss     bật/tắt QZSS
     * @param sbas     bật/tắt SBAS
     * @return Mảng byte chứa bản tin UBX-CFG-GNSS
     * @throws IllegalArgumentException nếu không có hệ thống nào được bật
     */
    public byte[] createConfigGNSSMessage(boolean gps, boolean sbas, boolean galileo, boolean beidou, boolean qzss, boolean glonass) {
        // Kiểm tra ít nhất 1 hệ thống được bật
        if (!atLeastOneGNSSIsEnabled(gps, glonass, galileo, beidou, qzss, sbas)) {
            return null;
        }

        int classID = 0x06; // UBX-CFG
        int messageID = 0x3E; // UBX-CFG-GNSS

        int payloadLength = 4 + 8 * 6; // Payload length: 4 bytes header + 8 bytes mỗi block (6 hệ thống GNSS)

        // Tạo payload
        byte[] payload = new byte[payloadLength];
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

        // Header cấu hình
        buffer.put((byte) 0x00); // msgVer: Version của bản tin
        buffer.put((byte) 0x00);   // numTrkChHw: Số kênh cứng hỗ trợ
        buffer.put((byte) 0x3C);   // numTrkChUse: Số kênh được sử dụng
        buffer.put((byte) 0x06);    // numConfigBlocks: Số block GNSS được cấu hình

        // Cấu hình cho từng GNSS
        configureGNSS(buffer, 0, gps, 0x08, 0x10);      // GPS
        configureGNSS(buffer, 1, sbas, 0x03, 0x03);    // SBAS (chỉ hỗ trợ 3 kênh)
        configureGNSS(buffer, 2, galileo, 0x08, 0x0C); // Galileo
        configureGNSS(buffer, 3, beidou, 0x02, 0x05);  // BeiDou
        configureGNSS(buffer, 5, qzss, 0x03, 0x04);    // QZSS (chỉ hỗ trợ 4 kênh)
        configureGNSS(buffer, 6, glonass, 0x08, 0x0C); // GLONASS

        // Tạo header UBX
        byte[] header = new byte[6];
        header[0] = (byte) 0xB5; // Sync char 1
        header[1] = (byte) 0x62; // Sync char 2
        header[2] = (byte) classID;
        header[3] = (byte) messageID;
        header[4] = (byte) (payloadLength & 0xFF);
        header[5] = (byte) ((payloadLength >> 8) & 0xFF);

        // Kết hợp header và payload
        byte[] message = new byte[header.length + payload.length + 2]; // +2 cho checksum
        System.arraycopy(header, 0, message, 0, header.length);
        System.arraycopy(payload, 0, message, header.length, payload.length);

        // Tính checksum
        int checksumA = 0, checksumB = 0;
        for (int i = 2; i < header.length + payload.length; i++) { // Bắt đầu từ Class ID
            checksumA = (checksumA + message[i]) & 0xFF;
            checksumB = (checksumB + checksumA) & 0xFF;
        }

        message[message.length - 2] = (byte) checksumA;
        message[message.length - 1] = (byte) checksumB;

        return message;
    }

    /**
     * Thêm cấu hình cho một hệ thống GNSS vào payload.
     *
     * @param buffer  Bộ đệm để ghi dữ liệu
     * @param gnssId  ID của hệ thống GNSS
     * @param enable  Bật/tắt hệ thống
     * @param maxTrk  Số kênh tối đa
     */
    private static void configureGNSS(ByteBuffer buffer, int gnssId, boolean enable, int resTrkCh, int maxTrk) {
        buffer.put((byte) gnssId);           // GNSS ID
        buffer.put((byte) resTrkCh);         // resTrkCh
        buffer.put((byte) maxTrk);     		 // maxTrkCh: Số kênh tối đa
        buffer.put((byte) 0x00);
        buffer.put((byte) (enable ? 0x01 : 0x00)); // Enable/Disable
        buffer.put((byte) 0x00); 					// Flags: Reserved
        buffer.put((byte) (enable ? 0x01 : 0x00)); 
        buffer.put((byte) 0x01);
        
    }

    /**
     * Kiểm tra nếu ít nhất một hệ thống GNSS được bật.	
     *
     * @param systems Các trạng thái bật/tắt của các hệ thống GNSS
     * @return true nếu ít nhất một hệ thống được bật, false nếu không
     */
    private static boolean atLeastOneGNSSIsEnabled(boolean... systems) {
        for (boolean system : systems) {
            if (system) {
                return true;
            }
        }
        return false;
    }
}

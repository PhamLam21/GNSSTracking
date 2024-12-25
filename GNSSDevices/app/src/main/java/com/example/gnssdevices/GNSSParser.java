package com.example.gnssdevices;

public class GNSSParser {

    private boolean gpsEnabled = false;
    private boolean glonassEnabled = false;
    private boolean galileoEnabled = false;
    private boolean beidouEnabled = false;

    public GNSSParser(byte[] ubxMessage) throws Exception {
        parseUbxCfgGnssMessage(ubxMessage);
    }

    private void parseUbxCfgGnssMessage(byte[] message) throws Exception {
        // Kiểm tra độ dài tối thiểu của thông điệp (header + length + payload + checksum)
        if (message.length < 8) {
            throw new Exception("Độ dài thông điệp UBX không hợp lệ");
        }

        // Tìm thông điệp UBX-CFG-GNSS trong mảng byte
        // Ký tự đồng bộ: 0xB5, 0x62
        int offset = 0;
        while (offset < message.length - 8) { // Thông điệp tối thiểu 8 byte
            if ((message[offset] == (byte) 0xB5) && (message[offset + 1] == (byte) 0x62)) {
                // Kiểm tra class và ID
                if ((message[offset + 2] == 0x06) && (message[offset + 3] == 0x3E)) {
                    // Tìm thấy thông điệp UBX-CFG-GNSS
                    break;
                }
            }
            offset++;
        }

        if (offset >= message.length - 8) {
            throw new Exception("Không tìm thấy thông điệp UBX-CFG-GNSS");
        }

        // Đọc độ dài (2 byte, little-endian)
        int length = ((message[offset + 5] & 0xFF) << 8) | (message[offset + 4] & 0xFF);

        // Kiểm tra đủ dữ liệu trong thông điệp
        if (offset + 6 + length + 2 > message.length) {
            throw new Exception("Thông điệp UBX-CFG-GNSS không đầy đủ");
        }

        // Trích xuất payload
        byte[] payload = new byte[length];
        System.arraycopy(message, offset + 6, payload, 0, length);

        // Phân tích payload
        int payloadOffset = 3;

        // Đọc số block cấu hình
        int numConfigBlocks = payload[payloadOffset++] & 0xFF;

        // Phân tích từng block cấu hình
        for (int i = 0; i < numConfigBlocks; i++) {
            if (payloadOffset + 8 > payload.length) {
                throw new Exception("Kết thúc payload không mong muốn");
            }

            int gnssId = payload[payloadOffset++] & 0xFF;
            payloadOffset += 3;

            // Đọc flags (4 byte, little-endian)
            int flags = ((payload[payloadOffset + 3] & 0xFF) << 24)
                    | ((payload[payloadOffset + 2] & 0xFF) << 16)
                    | ((payload[payloadOffset + 1] & 0xFF) << 8)
                    | (payload[payloadOffset] & 0xFF);
            payloadOffset += 4;

            // Kiểm tra xem GNSS có được kích hoạt không
            boolean enabled = (flags & 0x01) != 0;

            // Xác định hệ thống GNSS
            switch (gnssId) {
                case 0: // GPS
                    gpsEnabled = enabled;
                    break;
                case 1: // SBAS (bỏ qua hoặc xử lý nếu cần)
                    break;
                case 2: // Galileo
                    galileoEnabled = enabled;
                    break;
                case 3: // BeiDou
                    beidouEnabled = enabled;
                    break;
                case 4: // IMES (bỏ qua)
                    break;
                case 5: // QZSS (bỏ qua hoặc xử lý nếu cần)
                    break;
                case 6: // GLONASS
                    glonassEnabled = enabled;
                    break;
                default:
                    // GNSS ID không xác định
                    break;
            }
        }
    }

    // Các phương thức để lấy trạng thái của từng hệ thống GNSS
    public boolean isGpsEnabled() {
        return gpsEnabled;
    }

    public boolean isGlonassEnabled() {
        return glonassEnabled;
    }

    public boolean isGalileoEnabled() {
        return galileoEnabled;
    }

    public boolean isBeidouEnabled() {
        return beidouEnabled;
    }
}

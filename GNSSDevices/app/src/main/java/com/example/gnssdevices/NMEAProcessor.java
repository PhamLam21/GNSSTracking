package com.example.gnssdevices;
public class NMEAProcessor {

    private final StringBuilder buffer = new StringBuilder();
    private boolean collecting = false;

    /**
     * Xử lý byte nhận được từ UART.
     *
     * @param inputByte byte nhận được từ UART
     * @return Câu NMEA hoàn chỉnh (nếu có) hoặc null
     */
    public String processIncomingByte(int inputByte) {
        char receivedChar = (char) inputByte;

        // Bắt đầu một câu mới khi gặp ký tự '$'
        if (receivedChar == '$') {
            collecting = true;
            buffer.setLength(0); // Xóa bộ đệm cũ
            buffer.append(receivedChar);
        } else if (collecting) {
            buffer.append(receivedChar);

            // Kết thúc câu khi gặp '\n'
            if (receivedChar == '\n') {
                String sentence = buffer.toString().trim();
                collecting = false; // Hoàn tất câu

                if (sentence.length() > 1 && sentence.charAt(sentence.length() - 1) == '\r') {
                    sentence = sentence.substring(0, sentence.length() - 1);
                }

                // Kiểm tra checksum
                if (isValidNMEA(sentence)) {
                    return sentence; // Trả về câu NMEA hợp lệ
                }
                buffer.setLength(0); // Xóa bộ đệm
            }
        }

        return null;
    }

    /**
     * Kiểm tra xem câu NMEA có hợp lệ hay không bằng cách xác minh checksum.
     *
     * @param sentence Câu NMEA cần kiểm tra
     * @return true nếu hợp lệ, false nếu không
     */
    public boolean isValidNMEA(String sentence) {
        if (!sentence.startsWith("$") || !sentence.contains("*")) {
            return false;
        }

        // Tách phần dữ liệu và checksum
        int checksumIndex = sentence.lastIndexOf('*');
        String data = sentence.substring(1, checksumIndex); // Bỏ dấu '$'
        String checksumStr = sentence.substring(checksumIndex + 1);

        try {
            // Parse checksum từ chuỗi
            int expectedChecksum = Integer.parseInt(checksumStr, 16);
            int calculatedChecksum = calculateChecksum(data);

            return expectedChecksum == calculatedChecksum;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Tính checksum cho một chuỗi dữ liệu NMEA.
     *
     * @param data Chuỗi dữ liệu (không bao gồm $ và *)
     * @return Giá trị checksum
     */
    private int calculateChecksum(String data) {
        int checksum = 0;
        for (char ch : data.toCharArray()) {
            checksum ^= ch;
        }
        return checksum;
    }
}


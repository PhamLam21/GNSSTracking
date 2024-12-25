package com.example.gnssdevices;

public class NavStatusProcessor {

    private static final int SYNC_CHAR_1 = 0xB5;
    private static final int SYNC_CHAR_2 = 0x62;
    private static final int UBX_CLASS_NAV = 0x01;//class cho UBX-CFG-GNSS
    private static final int UBX_ID_NAV = 0x03; //ID cho UBX-CFG-GNSS

    private enum State {
        WAITING_FOR_SYNC1,
        WAITING_FOR_SYNC2,
        COLLECTING_HEADER,
        COLLECTING_PAYLOAD
    }

    private State currentState = State.WAITING_FOR_SYNC1;
    private final byte[] messageBuffer = new byte[1024]; // Buffer to store the message
    private int messageIndex = 0;
    private int payloadLength = 0; // Length of the payload

    public byte[] processIncomingByte(int inputByte) {
        switch (currentState) {
            case WAITING_FOR_SYNC1:
                if (inputByte == SYNC_CHAR_1) {
                    currentState = State.WAITING_FOR_SYNC2;
                }
                break;
            case WAITING_FOR_SYNC2:
                if (inputByte == SYNC_CHAR_2) {
                    messageBuffer[0] = (byte) SYNC_CHAR_1;
                    messageBuffer[1] = (byte) SYNC_CHAR_2;
                    messageIndex = 2;
                    currentState = State.COLLECTING_HEADER;
                } else {
                    resetProcessor();
                }
                break;
            case COLLECTING_HEADER:
                messageBuffer[messageIndex++] = (byte) inputByte;
                if (messageIndex == 6) { // Header complete
                    if (messageBuffer[2] == UBX_CLASS_NAV && messageBuffer[3] == UBX_ID_NAV) {
                        payloadLength = ((messageBuffer[4] & 0xFF) | (messageBuffer[5] & 0xFF) << 8) + 6 + 2; // +6 for header, +2 for checksum
                        currentState = State.COLLECTING_PAYLOAD;
                    } else {
                        // Reset if not a UBX-NAV-STATUS message
                        resetProcessor();
                    }
                }
                break;
            case COLLECTING_PAYLOAD:
                messageBuffer[messageIndex++] = (byte) inputByte;
                if (messageIndex == payloadLength) {
                    byte[] completeMessage = new byte[payloadLength];
                    System.arraycopy(messageBuffer, 0, completeMessage, 0, payloadLength);
                    resetProcessor();
                    if (validateChecksum(completeMessage)) {
                        resetProcessor();
                        return completeMessage;
                    } else {
                        resetProcessor(); // Invalid checksum
                    }
                }
                break;
        }
        return null;
    }

    private void resetProcessor() {
        currentState = State.WAITING_FOR_SYNC1;
        messageIndex = 0;
        payloadLength = 0;
    }

    private boolean validateChecksum(byte[] message) {
        int ckA = 0, ckB = 0;
        for (int i = 2; i < message.length - 2; i++) { // Skip SYNC and checksum
            ckA = (ckA + (message[i] & 0xFF)) & 0xFF;
            ckB = (ckB + ckA) & 0xFF;
        }
        return (message[message.length - 2] & 0xFF) == ckA &&
                (message[message.length - 1] & 0xFF) == ckB;
    }
}

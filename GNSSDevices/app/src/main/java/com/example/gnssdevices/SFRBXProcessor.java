package com.example.gnssdevices;

 class SFRBXProcessor {
    private static final int SYNC_CHAR_1 = 0xB5;
    private static final int SYNC_CHAR_2 = 0x62;
    private static final int UBX_CLASS_SFRBX = 0x02; // Class cho UBX-SFRBX
    private static final int UBX_ID_SFRBX = 0x13; // ID cho UBX-SFRBX

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

    public String processIncomingByte(int inputByte) {
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
                    if (messageBuffer[2] == UBX_CLASS_SFRBX && messageBuffer[3] == UBX_ID_SFRBX) {
                        payloadLength = ((messageBuffer[4] & 0xFF) | (messageBuffer[5] & 0xFF) << 8) + 6 + 2; // +6 for header, +2 for checksum
                        currentState = State.COLLECTING_PAYLOAD;
                    } else {
                        // Reset if not a UBX-SFRBX message
                        resetProcessor();
                    }
                }
                break;
            case COLLECTING_PAYLOAD:
//                if(payloadLength == 56) {
                    messageBuffer[messageIndex++] = (byte) inputByte;
                    if (messageIndex == payloadLength) {
                        byte[] completeByteMessage = new byte[payloadLength];
                        System.arraycopy(messageBuffer, 0, completeByteMessage, 0, payloadLength);
                        if (validateChecksum(completeByteMessage)) {
                            String completeMessage = bytesToHex(completeByteMessage, messageIndex);
                            resetProcessor();
                            return completeMessage;
                        } else {
                            resetProcessor(); // Invalid checksum
                        }
                        resetProcessor();
                    }
//                } else {
//                    resetProcessor();
//                }
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

    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
package com.ytyo.Dispatcher.Message;

import java.util.Optional;


public enum FrameCode {
    CLOSE((short) 1),
    CLOSE_ACK((short) 2),
    OPEN((short) 3),
    OPEN_ACK((short) 4),
    MESSAGE((short) 5),
    PING((short) 6),
    PONG((short) 7),
    AUTH((short) 8),
    TEST((short) 9),
    TEST_REPLY((short) 10);
    private final short code;

    FrameCode(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static Optional<FrameCode> from(short code) {

        return switch (code) {

            case 1 -> Optional.of(CLOSE);

            case 2 -> Optional.of(CLOSE_ACK);

            case 3 -> Optional.of(OPEN);

            case 4 -> Optional.of(OPEN_ACK);

            case 5 -> Optional.of(MESSAGE);

            case 6 -> Optional.of(PING);

            case 7 -> Optional.of(PONG);

            case 8 -> Optional.of(AUTH);

            case 9 -> Optional.of(TEST);

            case 10 -> Optional.of(TEST_REPLY);

            default -> Optional.empty();
        };
    }
}

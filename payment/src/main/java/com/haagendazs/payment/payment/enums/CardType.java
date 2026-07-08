package com.haagendazs.payment.payment.enums;

public enum CardType {
    CREDIT("신용"),
    CHECK("체크"),
    GIFT("기프트");

    private final String krName;

    CardType(String krName) {
        this.krName = krName;
    }

    public String getKrName() {
        return krName;
    }

    public static CardType from(String value) {
        for (CardType cardType : values()) {
            if (cardType.name().equals(value) || cardType.krName.equals(value)) {
                return cardType;
            }
        }

        throw new IllegalArgumentException("지원하지 않는 카드 타입입니다. value=" + value);
    }
}

package com.haagendazs.payment.payment.enums;

public enum OwnerType {
    INDIVIDUAL("개인"),
    CORPORATE("법인"),
    UNKNOWN("미확인");

    private final String krName;

    OwnerType(String krName) {
        this.krName = krName;
    }

    public String getKrName() {
        return krName;
    }

    public static OwnerType from(String value) {
        for (OwnerType ownerType : values()) {
            if (ownerType.name().equals(value) || ownerType.krName.equals(value)) {
                return ownerType;
            }
        }

        throw new IllegalArgumentException("지원하지 않는 카드 소유자 타입입니다. value=" + value);
    }
}

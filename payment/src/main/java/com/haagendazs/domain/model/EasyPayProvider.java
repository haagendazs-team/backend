package com.haagendazs.domain.model;

//간편결제사
public enum EasyPayProvider {
    TOSSPAY("토스페이", "TOSSPAY"),
    NAVERPAY("네이버페이", "NAVERPAY"),
    SAMSUNGPAY("삼성페이", "SAMSUNGPAY"),
    APPLEPAY("애플페이", "APPLEPAY"),
    LPAY("엘페이", "LPAY"),
    KAKAOPAY("카카오페이", "KAKAOPAY"),
    PINPAY("핀페이", "PINPAY"),
    PAYCO("페이코", "PAYCO"),
    SSG("SSG페이", "SSG");

    private final String krName;
    private final String enName;

    EasyPayProvider(String krName, String enName){
        this.krName = krName;
        this.enName = enName;
    }

    public String getKrName() { return krName; }
    public String getEnName() { return enName; }
}

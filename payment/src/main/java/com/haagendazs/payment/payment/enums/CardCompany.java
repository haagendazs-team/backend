package com.haagendazs.payment.payment.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

//카드사
public enum CardCompany {

    IBK_BC("3K", "기업비씨", "IBK_BC"),
    GWANGJU_BANK("46", "광주", "GWANGJUBANK"),
    LOTTE("71", "롯데", "LOTTE"),
    KDB_BANK("30", "산업", "KDBBANK"),
    BC("31", null, "BC"),
    SAMSUNG("51", "삼성", "SAMSUNG"),
    SAEMAUL("38", "새마을", "SAEMAUL"),
    SHINHAN("41", "신한", "SHINHAN"),
    SHINHYEOP("62", "신협", "SHINHYEOP"),
    CITI("36", "씨티", "CITI"),
    WOORI_BC("33", "우리", "WOORI"),
    WOORI("W1", "우리", "WOORI"),
    POST("37", "우체국", "POST"),
    SAVING_BANK("39", "저축", "SAVINGBANK"),
    JEONBUK_BANK("35", "전북", "JEONBUKBANK"),
    JEJU_BANK("42", "제주", "JEJUBANK"),
    KAKAO_BANK("15", "카카오뱅크", "KAKAOBANK"),
    K_BANK("3A", "케이뱅크", "KBANK"),
    TOSS_BANK("24", "토스뱅크", "TOSSBANK"),
    HANA("21", "하나", "HANA"),
    HYUNDAI("61", "현대", "HYUNDAI"),
    KOOKMIN("11", "국민", "KOOKMIN"),
    NONGHYEOP("91", "농협", "NONGHYEOP"),
    SUHYEOP("34", "수협", "SUHYEOP");

    private static final Map<String, CardCompany> CODE_MAP =
            Arrays.stream(values())
                    .collect(Collectors.toUnmodifiableMap(
                            CardCompany::getIssuerCode,
                            Function.identity()
                    ));

    private final String issuerCode;
    private final String krName;
    private final String enName;

    CardCompany(String issuerCode, String krName, String enName) {
        this.issuerCode = issuerCode;
        this.krName = krName;
        this.enName = enName;
    }

    public String getIssuerCode() {
        return issuerCode;
    }

    public String getKrName() {
        return krName;
    }

    public String getEnName() {
        return enName;
    }

    public static CardCompany fromCode(String issuerCode) {
        CardCompany cardCompany = CODE_MAP.get(issuerCode);

        if (cardCompany == null) {
            throw new IllegalArgumentException("지원하지 않는 카드사 코드입니다. code=" + issuerCode);
        }

        return cardCompany;
    }
}

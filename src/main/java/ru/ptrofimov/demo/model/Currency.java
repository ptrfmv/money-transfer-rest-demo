package ru.ptrofimov.demo.model;

public enum Currency {
    AMERICAN_DOLLAR("USD"),
    RUSSIAN_RUBLE("RUR"),
    EURO("EUR"),
    JAPANESE_YEN("YEN");

    private String shortName;

    Currency(String shortName) {
        this.shortName = shortName;
    }

    public String getShortName() {
        return shortName;
    }

    public static Currency fromString(String name) {
        for (Currency currency : values()) {
            if (currency.getShortName().equals(name))
                return currency;
        }
        throw new IllegalArgumentException();
    }
}

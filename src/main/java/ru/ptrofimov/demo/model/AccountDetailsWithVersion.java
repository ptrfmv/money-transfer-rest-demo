package ru.ptrofimov.demo.model;

public class AccountDetailsWithVersion extends AccountDetails {
    private int version;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}

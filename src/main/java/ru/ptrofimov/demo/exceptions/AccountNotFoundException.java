package ru.ptrofimov.demo.exceptions;

public class AccountNotFoundException extends Exception {
    private long accountId;

    public AccountNotFoundException(long accountId) {
        this.accountId = accountId;
    }

    public long getAccountId() {
        return accountId;
    }
}

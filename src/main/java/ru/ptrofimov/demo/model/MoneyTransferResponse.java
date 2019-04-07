package ru.ptrofimov.demo.model;

import java.util.Objects;

public class MoneyTransferResponse {

    private MoneyTransferStatus status;

    public MoneyTransferResponse() {
    }

    public MoneyTransferResponse(MoneyTransferStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public MoneyTransferStatus getStatus() {
        return status;
    }

    public void setStatus(MoneyTransferStatus status) {
        this.status = status;
    }
}

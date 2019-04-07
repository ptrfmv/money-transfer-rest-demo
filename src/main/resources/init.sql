CREATE TABLE ACCOUNTS (
    ID IDENTITY NOT NULL,
    CURRENCY CHAR(3) NOT NULL,
    BALANCE DECIMAL(20, 2) NOT NULL DEFAULT 0,
    OWNER VARCHAR(56),
);
INSERT INTO ACCOUNTS (CURRENCY, BALANCE, OWNER) VALUES ('RUR', 1000, 'owner1');
INSERT INTO ACCOUNTS (CURRENCY, BALANCE, OWNER) VALUES ('USD', 200, 'owner2');
INSERT INTO ACCOUNTS (CURRENCY, BALANCE, OWNER) VALUES ('EUR', 150, 'owner3');
INSERT INTO ACCOUNTS (CURRENCY, BALANCE, OWNER) VALUES ('RUR', 500, 'owner4');
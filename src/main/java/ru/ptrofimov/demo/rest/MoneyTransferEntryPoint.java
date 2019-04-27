package ru.ptrofimov.demo.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ptrofimov.demo.exceptions.AccountNotFoundException;
import ru.ptrofimov.demo.exceptions.InsufficientFundsException;
import ru.ptrofimov.demo.model.*;
import ru.ptrofimov.demo.utils.DBUtils;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Objects;

import static ru.ptrofimov.demo.rest.PathConstants.*;

@Path("/" + MONEY_TRANSFER_ENTRY_POINT)
public class MoneyTransferEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(MoneyTransferEntryPoint.class);

    static final String GREETING_TEXT = "I'm a lean mean money transferring machine";

    @GET
    @Path(GREETING_PATH)
    @Produces(MediaType.TEXT_PLAIN)
    public String greet() {
        return GREETING_TEXT;
    }

    @POST
    @Path(ACCOUNTS)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public AccountDetails createAccount(AccountDetails accountDetails) throws SQLException {
        try {
            Currency currency = Objects.requireNonNull(accountDetails.getCurrency());
            BigDecimal balance = Objects.requireNonNull(accountDetails.getBalance());
            String owner = Objects.requireNonNull(accountDetails.getOwner());
            long insertedId;
            try (Connection connection = DBUtils.getConnection()) {
                logger.trace("inserting currency = {} balance = {} owner = {}", currency, balance, owner);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO ACCOUNTS (CURRENCY, BALANCE, OWNER) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, currency.getShortName());
                    statement.setBigDecimal(2, balance);
                    statement.setString(3, owner);
                    statement.executeUpdate();
                    try (ResultSet resultSet = statement.getGeneratedKeys()) {
                        resultSet.next();
                        insertedId = resultSet.getLong(1);
                    }
                }
            }
            AccountDetails result = new AccountDetails();
            result.setId(insertedId);
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    @GET
    @Path(ACCOUNTS + "/{accountId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAccountDetails(@PathParam("accountId") long accountId) throws SQLException {
        try (Connection connection = DBUtils.getConnection()) {
            AccountDetails result = getAccountDetails(connection, accountId);
            return Response.ok(result).build();
        } catch (AccountNotFoundException accEx) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    private static AccountDetails getAccountDetails(Connection connection, long accountId) throws SQLException, AccountNotFoundException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT CURRENCY, BALANCE, OWNER FROM ACCOUNTS WHERE ID = ?")) {
            statement.setLong(1, accountId);
            statement.execute();
            try (ResultSet resultSet = statement.getResultSet()) {
                if (resultSet.next()) {
                    AccountDetails result = new AccountDetails();
                    result.setCurrency(Currency.fromString(resultSet.getString(1)));
                    result.setBalance(resultSet.getBigDecimal(2));
                    result.setOwner(resultSet.getString(3));
                    result.setId(accountId);
                    return result;
                }
            }
        }
        throw new AccountNotFoundException();
    }

    @POST
    @Path(ACCOUNTS + "/{accountId}/balance")
    @Produces(MediaType.APPLICATION_JSON)
    public Response transferMoney(@PathParam("accountId") long recipientId,
                                  @FormParam("from") long senderId,
                                  @FormParam("amount") BigDecimal amount) throws SQLException {
        if (recipientId == 0 || senderId == 0 || recipientId == senderId ||
                amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try (Connection connection = DBUtils.getConnection()) {
            connection.setAutoCommit(false);

            AccountDetails recipientAccountDetails;
            try {
                recipientAccountDetails = getAccountDetails(connection, recipientId);
            } catch (AccountNotFoundException accEx) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            AccountDetails senderAccountDetails;
            try {
                senderAccountDetails = getAccountDetails(connection, senderId);
            } catch (AccountNotFoundException accEx) {
                return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.ACCOUNT_NOT_FOUND)).build();
            }

            if (recipientAccountDetails.getCurrency() != senderAccountDetails.getCurrency()) {
                return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.CURRENCY_MISMATCH)).build();
            }

            try {
                transferMoney(connection, senderId, recipientId, amount);
                connection.commit();
                return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.SUCCESS)).build();
            } catch (SQLNonTransientException sqlException) {
                connection.rollback();
                if (sqlException.getCause() instanceof InsufficientFundsException) {
                    return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.INSUFFICIENT_FUNDS)).build();
                } else {
                    throw sqlException;
                }
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    private static void transferMoney(Connection connection, long senderId, long recipientId, BigDecimal amount) throws SQLException {
        long first, second;
        BigDecimal firstAmount, secondAmount;
        if (senderId <= recipientId) {
            first = senderId;
            second = recipientId;
            firstAmount = amount.negate();
            secondAmount = amount;
        } else {
            first = recipientId;
            second = senderId;
            firstAmount = amount;
            secondAmount = amount.negate();
        }
        updateBalance(connection, first, firstAmount);
        updateBalance(connection, second, secondAmount);
    }

    private static void updateBalance(Connection connection, long accountId, BigDecimal amount) throws SQLException {
        try (PreparedStatement statement = connection
                .prepareStatement("UPDATE ACCOUNTS SET BALANCE = BALANCE + ? WHERE ID = ?")) {
            statement.setBigDecimal(1, amount);
            statement.setLong(2, accountId);
            statement.executeUpdate();
        }
    }
}

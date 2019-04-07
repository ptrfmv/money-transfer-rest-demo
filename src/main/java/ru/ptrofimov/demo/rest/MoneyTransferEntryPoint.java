package ru.ptrofimov.demo.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ptrofimov.demo.model.AccountDetails;
import ru.ptrofimov.demo.model.Currency;
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
    @Produces(MediaType.APPLICATION_JSON)
    public AccountDetails createAccount(@FormParam("currency") Currency currency,
                                        @FormParam("balance") BigDecimal balance,
                                        @FormParam("owner") String owner) throws SQLException {
        try {
            Objects.requireNonNull(currency);
            Objects.requireNonNull(balance);
            Objects.requireNonNull(owner);
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
            logger.error(e.getMessage() ,e);
            throw e;
        }
    }

    @GET
    @Path(ACCOUNTS + "/{accountId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAccountDetails(@PathParam("accountId") long accountId) throws SQLException {
        try {
            AccountDetails result = null;
            try (Connection connection = DBUtils.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT CURRENCY, BALANCE, OWNER FROM ACCOUNTS WHERE ID = ?")) {
                    statement.setLong(1, accountId);
                    statement.execute();
                    try (ResultSet resultSet = statement.getResultSet()) {
                        if (resultSet.next()) {
                            result = new AccountDetails();
                            result.setCurrency(Currency.fromString(resultSet.getString(1)));
                            result.setBalance(resultSet.getBigDecimal(2));
                            result.setOwner(resultSet.getString(3));
                        }
                    }
                }
            }
            if (result == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok().entity(result).build();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }
}

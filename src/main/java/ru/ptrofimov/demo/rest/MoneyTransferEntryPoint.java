package ru.ptrofimov.demo.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ptrofimov.demo.exceptions.AccountNotFoundException;
import ru.ptrofimov.demo.exceptions.CurrencyMismatchException;
import ru.ptrofimov.demo.exceptions.InsufficientFundsException;
import ru.ptrofimov.demo.logic.AccountHelper;
import ru.ptrofimov.demo.model.AccountDetails;
import ru.ptrofimov.demo.model.Currency;
import ru.ptrofimov.demo.model.MoneyTransferResponse;
import ru.ptrofimov.demo.model.MoneyTransferStatus;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
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
    public AccountDetails createAccount(AccountDetails accountDetails) throws Exception {
        try {
            Currency currency = Objects.requireNonNull(accountDetails.getCurrency());
            BigDecimal balance = Objects.requireNonNull(accountDetails.getBalance());
            String owner = Objects.requireNonNull(accountDetails.getOwner());
            try (AccountHelper helper = new AccountHelper()) {
                logger.trace("inserting currency = {} balance = {} owner = {}", currency, balance, owner);
                return helper.createAccount(currency, balance, owner);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    @GET
    @Path(ACCOUNTS + "/{accountId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAccountDetails(@PathParam("accountId") long accountId) throws Exception {
        try (AccountHelper helper = new AccountHelper()) {
            return Response.ok(helper.getAccountDetails(accountId)).build();
        } catch (AccountNotFoundException accEx) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    @POST
    @Path(ACCOUNTS + "/{accountId}/balance")
    @Produces(MediaType.APPLICATION_JSON)
    public Response transferMoney(@PathParam("accountId") long recipientId,
                                  @FormParam("from") long senderId,
                                  @FormParam("amount") BigDecimal amount) throws Exception {
        if (recipientId == 0 || senderId == 0 || recipientId == senderId ||
                amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try (AccountHelper helper = new AccountHelper()) {
            helper.transferMoney(senderId, recipientId, amount);
            return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.SUCCESS)).build();
        } catch (AccountNotFoundException anfe) {
            if (anfe.getAccountId() == recipientId) {
                return Response.status(Response.Status.NOT_FOUND).build();
            } else {
                return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.ACCOUNT_NOT_FOUND)).build();
            }
        } catch (CurrencyMismatchException cme) {
            return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.CURRENCY_MISMATCH)).build();
        } catch (InsufficientFundsException ife) {
            return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.INSUFFICIENT_FUNDS)).build();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }
}

# Payment attempt reconciliation

A Checkout attempt is persisted before the request to Stripe. If the response
is lost, the local `sessionId` may be empty even though a session exists and can
be paid. Recreating such a session is blocked after 23 hours because Stripe may
prune idempotency keys after 24 hours. See
[Stripe idempotency](https://docs.stripe.com/api/idempotent_requests).

## Background reconciliation

`PAYMENT_RECONCILIATION_ENABLED=true` enables the job. Its interval is configured
by `PAYMENT_RECONCILIATION_DELAY_MS`, defaulting to 300000 milliseconds (five
minutes). Each run checks up to 100 `PENDING` attempts at least 23 hours old.
Stripe requests execute outside database transactions and booking locks.

- Known session: Stripe verifies the amount, currency, and booking metadata.
  A paid attempt becomes `PAID`; an expired unpaid session becomes `EXPIRED`.
  A session still being processed remains `PENDING` and is checked again.
- Unknown session: the attempt becomes `RECONCILIATION_REQUIRED` without
  creating another session. Administrators can find it through `GET /payments`.
- Provider failure: the payment outcome is unchanged, and a later run retries
  the check. Logs contain the payment ID and error type, without Stripe
  credentials or session data.

Migration `018-payment-reconciliation` extends the unique index so a booking
can have only one attempt in `PENDING` or `RECONCILIATION_REQUIRED`. A new
payment cannot bypass an unresolved attempt, even if newer records exist.

## Administrative reconciliation

1. Select a payment ID with status `RECONCILIATION_REQUIRED`.
2. Find the original Checkout Session in Stripe using its `paymentId` and
   `bookingId` metadata or request logs containing `booking-payment-<paymentId>`.
3. With an administrator JWT, call `POST /payments/<paymentId>/reconcile`:

   ```json
   {"sessionId": "cs_test_..."}
   ```

The server retrieves the session through the Stripe API. Before binding a lost
session, it verifies `paymentId`, `bookingId`, amount, and currency. The client
cannot specify the status, amount, or payment outcome. Incorrect metadata, a
different session ID for an already bound attempt, or an unverified state
returns `409`; customer access returns `403`.

`PAID` atomically confirms an active booking and creates one outbox event.
A verified `EXPIRED` result allows a new attempt through the normal
`POST /payments` endpoint. An open or processing session remains unresolved.
Repeating reconciliation does not duplicate events or downgrade `PAID`.
A late payment does not reopen a `CANCELED` or `EXPIRED` booking.

If the session cannot be found, do not manually mark the attempt as complete
or delete it: a missing session ID does not prove that no charge occurred.
Investigate the outcome in Stripe. This operation does not issue refunds or
release capacity without evidence of the payment outcome.

## Deployment

An unfinished cancellation has status `CANCELING`; an unfinished stay
expiration has status `EXPIRING`. These states block new checkout attempts and
date changes, retaining capacity until Stripe sessions are verifiably closed.
A temporary provider failure leaves the closure intent in the database: retry
the request or wait for the booking closure recovery job. If an old attempt has
lost its session ID, perform administrative reconciliation first. If the final
outbox write fails, payment outcomes remain persisted and the booking stays in
its intermediate state until the final transaction succeeds on retry.

Apply the Liquibase migration before enabling the new application version.
Older replicas do not support the new statuses and must not process payments
alongside the new version once reconciliation or booking closure is enabled.
Before rollback, complete all `CANCELING`/`EXPIRING` operations and stop the jobs
and new replicas. Migration rollback changes `RECONCILIATION_REQUIRED` back to
`PENDING`, retaining the restriction on duplicate unresolved attempts.
A booking is not automatically canceled merely because a callback is missing
or a payment attempt is old.

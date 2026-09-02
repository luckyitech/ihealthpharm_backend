-- ===========================================================================
--  DUPLICATE DEBT RECORDS
--  Bills that carry more than one open debt record against them.
--
--  READ ONLY. This only SELECTs - it changes nothing.
--
--  Each bill appears once per record, so its records sit together. Read it as:
--  "this bill really owes X, but these N records between them claim Y".
--
--  MATCHES BILL = YES  the record's own figures add up to the bill total
--                      (what it received + what it still claims = bill total),
--                      which is what a single correct record looks like.
--
--  BILL ALREADY SETTLED = YES  the bill itself has nothing left to pay, so
--                      every claim against it is phantom.
-- ===========================================================================
WITH open_rows AS (
    SELECT  ar.RECEIPT_NO                                                   AS record_ref,
            COALESCE(ar.BILL_NO, ar.SOURCE_REF)                             AS bill_ref,
            ar.CUSTOMER_NAME                                                AS customer,
            ar.MASTER_ACC_NO                                                AS account,
            COALESCE(ar.AMOUNT_RECEIVED, 0)                                 AS record_received,
            ar.AMOUNT_TO_BE_RECEIVED                                        AS record_claims,
            ar.PAYMENT_STATUS                                               AS record_status,
            DATE(ar.CREATION_TS)                                            AS record_created,
            COALESCE(s1.NET_AMOUNT,     s2.NET_AMOUNT,     s3.NET_AMOUNT)     AS bill_total,
            COALESCE(s1.PAID_AMOUNT,    s2.PAID_AMOUNT,    s3.PAID_AMOUNT)    AS bill_paid,
            COALESCE(s1.BALANCE_AMOUNT, s2.BALANCE_AMOUNT, s3.BALANCE_AMOUNT) AS bill_owes
    FROM        account_receivables ar
    LEFT JOIN   sales s1 ON s1.BILL_ID   = ar.SALES_BILL_ID
    LEFT JOIN   sales s2 ON s2.BILL_CODE = ar.BILL_NO
    LEFT JOIN   sales s3 ON s3.BILL_CODE = ar.SOURCE_REF
    WHERE   ar.SOURCE_TYPE    = 'Sales Billing'
      AND   ar.PAYMENT_STATUS IN ('Pending', 'Partially Paid')
      AND   ar.AMOUNT_TO_BE_RECEIVED > 0
      AND   COALESCE(ar.ACTIVE_S, 'Y') = 'Y'
),
duplicated AS (
    SELECT   bill_ref,
             COUNT(*)           AS records_on_bill,
             SUM(record_claims) AS total_claimed
    FROM     open_rows
    GROUP BY bill_ref
    HAVING   COUNT(*) > 1
)
SELECT  o.customer                                              AS `Customer`,
        o.account                                               AS `Account`,
        o.bill_ref                                              AS `Bill Reference`,
        o.bill_total                                            AS `Bill Total`,
        o.bill_paid                                             AS `Paid On Bill`,
        o.bill_owes                                             AS `Bill Still Owes`,
        CASE WHEN o.bill_owes <= 0 THEN 'YES' ELSE 'no' END     AS `Bill Already Settled`,
        d.records_on_bill                                       AS `Records On Bill`,
        d.total_claimed                                         AS `Records Claim`,
        ROUND(d.total_claimed - GREATEST(COALESCE(o.bill_owes, 0), 0), 2)
                                                                AS `Phantom Debt`,
        o.record_ref                                            AS `Record Reference`,
        o.record_received                                       AS `Record Received`,
        o.record_claims                                         AS `Record Claims`,
        o.record_status                                         AS `Record Status`,
        o.record_created                                        AS `Record Created`,
        CASE WHEN ABS((o.record_received + o.record_claims) - o.bill_total) < 0.01
             THEN 'YES' ELSE 'no' END                           AS `Matches Bill`
FROM        open_rows o
JOIN        duplicated d ON d.bill_ref = o.bill_ref
ORDER BY    (d.total_claimed - GREATEST(COALESCE(o.bill_owes, 0), 0)) DESC,
            o.bill_ref,
            o.record_created;

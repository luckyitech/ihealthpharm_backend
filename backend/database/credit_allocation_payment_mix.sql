-- ---------------------------------------------------------------------------
-- Credit settlement: keep the receipt number off the receivable, and restore
-- the sales bill's payment mix on reversal.
--
-- Two faults this corrects, both found by diffing whole rows before and after
-- a settlement was applied and then reversed:
--
--   1. The payment's receipt number was being written onto the receivable's
--      RECEIPT_NO, which is the receivable's own generated identifier (DPAR...)
--      and is what the receipt and statement reports print. It belongs on the
--      settlement batch instead.
--
--   2. Settling a credit bill with cash moves the amount into the bill's
--      CASH_AMOUNT and out of CREDIT_AMOUNT. Neither was recorded, so a
--      reversal left the bill showing the money as still paid in cash.
-- ---------------------------------------------------------------------------

ALTER TABLE `credit_allocation_batch`
  ADD COLUMN `RECEIPT_NUMBER` VARCHAR(25) NULL AFTER `PAYMENT_REF`;

ALTER TABLE `credit_allocation_line`
  ADD COLUMN `PRIOR_BILL_CASH_AMOUNT` DECIMAL(15,2) NULL AFTER `PRIOR_BILL_CHEQUE_DATE`,
  ADD COLUMN `PRIOR_BILL_CREDIT_AMOUNT` DECIMAL(15,2) NULL AFTER `PRIOR_BILL_CASH_AMOUNT`;

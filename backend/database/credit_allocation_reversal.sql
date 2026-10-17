-- ============================================================================
-- Credit settlement - reversal support
-- ============================================================================
--   mysql -u root -p ihealthpharm < backend/database/credit_allocation_reversal.sql
--
-- Settling a bill changes eight fields on its receivable, but only two were
-- being captured. The rest would have to be inferred to reverse, and STATUS
-- cannot be: it moves to Approved, yet rows are sometimes already Approved
-- beforehand, so there is no way to tell what to restore. The remaining prior
-- values are recorded here instead of guessed.
--
-- The batch keeps the credit limit as it stood before settling, for the same
-- reason - reversing by subtracting the allocated amount assumes nothing else
-- moved the limit in between.
-- ============================================================================


ALTER TABLE `credit_allocation_line`
  ADD COLUMN `PRIOR_AMOUNT_RECEIVED` decimal(15,2) DEFAULT NULL
      COMMENT 'amountReceived before settling - settlement adds to it rather than replacing'
      AFTER `PRIOR_BALANCE`,
  ADD COLUMN `PRIOR_PARTIAL_AMT`     decimal(15,2) DEFAULT NULL
      COMMENT 'Null and zero mean different things here, so the original is kept'
      AFTER `PRIOR_AMOUNT_RECEIVED`,
  ADD COLUMN `PRIOR_STATUS`          varchar(20)   DEFAULT NULL
      COMMENT 'Approved / Not Approved before settling - not inferable afterwards'
      AFTER `PRIOR_PAYMENT_STATUS`,
  ADD COLUMN `PRIOR_PAYMENT_TYPE`    varchar(20)   DEFAULT NULL AFTER `PRIOR_STATUS`,
  ADD COLUMN `PRIOR_APPROVED_BY`     int           DEFAULT NULL AFTER `PRIOR_PAYMENT_TYPE`,
  ADD COLUMN `PRIOR_APPROVED_DATE`   date          DEFAULT NULL AFTER `PRIOR_APPROVED_BY`;


ALTER TABLE `credit_allocation_batch`
  ADD COLUMN `PRIOR_CREDIT_LIMIT_LEFT` decimal(15,2) DEFAULT NULL
      COMMENT 'master_account.CREDIT_LIMIT_LEFT before settling'
      AFTER `MASTER_ACCOUNT_ID`,
  ADD COLUMN `REVERSED_BY`             int           DEFAULT NULL AFTER `REVERSAL_REASON`,
  ADD COLUMN `REVERSED_DATE`           datetime      DEFAULT NULL AFTER `REVERSED_BY`;

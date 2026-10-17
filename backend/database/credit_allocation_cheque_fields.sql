-- ============================================================================
-- Credit settlement - cheque details
-- ============================================================================
--   mysql -u root -p ihealthpharm < backend/database/credit_allocation_cheque_fields.sql
--
-- Two defects this closes.
--
-- The cheque date was never kept on the batch, so approving a held cheque had
-- nothing to settle with and fell back to the date the batch was recorded - the
-- sales bill then carried a date the cheque never had.
--
-- Settling by cheque writes the number, amount and date onto the receivable,
-- but reversal had no record of what was there before, so a bounced cheque left
-- its details behind permanently.
-- ============================================================================

ALTER TABLE `credit_allocation_batch`
  ADD COLUMN `CHEQUE_DATE` date DEFAULT NULL
      COMMENT 'Date on the cheque, as given when the payment was recorded'
      AFTER `CHEQUE_ID`;

ALTER TABLE `credit_allocation_line`
  ADD COLUMN `PRIOR_CHEQUE_NUMBER` varchar(30)   DEFAULT NULL AFTER `PRIOR_APPROVED_DATE`,
  ADD COLUMN `PRIOR_CHEQUE_AMT`    decimal(15,2) DEFAULT NULL AFTER `PRIOR_CHEQUE_NUMBER`,
  ADD COLUMN `PRIOR_CHEQUE_DT`     date          DEFAULT NULL AFTER `PRIOR_CHEQUE_AMT`;

-- Settling by cheque also appends the cheque number to the sales bill and adds
-- to its cheque amount. Without the originals, reversing left the number listed
-- twice and the amount overstated.
ALTER TABLE `credit_allocation_line`
  ADD COLUMN `PRIOR_BILL_CHEQUE_NUMBER` varchar(50)   DEFAULT NULL AFTER `PRIOR_CHEQUE_DT`,
  ADD COLUMN `PRIOR_BILL_CHEQUE_AMT`    decimal(15,2) DEFAULT NULL AFTER `PRIOR_BILL_CHEQUE_NUMBER`;

-- The bill's cheque date is overwritten too, and was the one field the
-- column-by-column verification missed until the whole row was compared.
ALTER TABLE `credit_allocation_line`
  ADD COLUMN `PRIOR_BILL_CHEQUE_DATE` varchar(30) DEFAULT NULL AFTER `PRIOR_BILL_CHEQUE_AMT`;

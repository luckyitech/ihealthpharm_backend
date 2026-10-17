-- ============================================================================
-- Credit settlement - issue a receipt the customer can be given
-- ============================================================================
--   mysql -u root -p ihealthpharm < backend/database/credit_allocation_receipt_number.sql
--
-- Settling now stamps one receipt number across every bill the payment touched,
-- in account_receivables.UNIQUE_RECEIPT_NO, which is how the Receipts screen
-- already groups a bulk payment into a single printed receipt - DPARR0205260003
-- covers 31 bills. The existing ACCOUNT_RECEIVABLES_RECEIPT report then prints
-- a settlement exactly as it prints a payment taken by hand.
--
-- Because settling now writes that column, reversing has to put back whatever
-- was there before, or a reversed bill stays attached to a receipt it is no
-- longer part of. Every other field settlement touches is already recorded this
-- way; this is the one it did not have.
-- ============================================================================

ALTER TABLE `credit_allocation_line`
  ADD COLUMN `PRIOR_UNIQUE_RECEIPT_NO` varchar(45) DEFAULT NULL
      COMMENT 'account_receivables.UNIQUE_RECEIPT_NO before settling, so reversal restores it'
      AFTER `PRIOR_BILL_CREDIT_AMOUNT`;

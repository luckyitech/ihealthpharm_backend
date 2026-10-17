-- ============================================================================
-- Credit settlement - reverse the credit limit by what was actually released
-- ============================================================================
--   mysql -u root -p ihealthpharm < backend/database/credit_allocation_credit_limit.sql
--
-- Reversing a settlement put CREDIT_LIMIT_LEFT back to the figure recorded when
-- it was applied. That discards everything that happened in between: an account
-- whose limit had since absorbed a 5,000 credit sale was restored to the older,
-- higher figure and handed that 5,000 back as available credit, free to
-- overdraw by it. Every other path that moves this column adds or subtracts a
-- delta for exactly this reason; only the reversal was restoring an absolute.
--
-- The amount released cannot be inferred from the settlement either. It is
-- usually the allocated amount - 30,000 settled moved a limit from 22,886 to
-- 52,886 - but not always: a receivable that names its bill only in SOURCE_REF
-- never reaches the account at all, and 1,360 settled against one moved the
-- limit by nothing. So the difference is measured when the settlement is
-- applied and recorded here, and the reversal subtracts exactly that.
--
-- Null means a batch settled before this column existed; those still reverse
-- the old way, from PRIOR_CREDIT_LIMIT_LEFT.
-- ============================================================================

ALTER TABLE `credit_allocation_batch`
  ADD COLUMN `CREDIT_LIMIT_RELEASED` decimal(15,2) DEFAULT NULL
      COMMENT 'How much credit settling actually freed, measured across the receivables update'
      AFTER `PRIOR_CREDIT_LIMIT_LEFT`;

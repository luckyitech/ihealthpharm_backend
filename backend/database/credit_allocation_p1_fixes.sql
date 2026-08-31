-- ============================================================================
-- Credit settlement - P1 fixes
-- ============================================================================
--   mysql -u root -p ihealthpharm < backend/database/credit_allocation_p1_fixes.sql
--
-- Code-only fixes accompany this file; the one schema change it needs is an
-- index.
--
-- Rebuilding a bill's balance now gathers its receivables the same way the
-- open-bill list names them - BILL_NO where it is set, SOURCE_REF where it is
-- not. Matching on BILL_NO alone missed the 436 settleable rows that keep the
-- code only in SOURCE_REF, and settling one of those rebuilt its bill from a
-- sum of nothing: the payments already recorded were wiped and the debt
-- restated as the full invoice.
--
-- SOURCE_REF is already indexed. BILL_NO is not, so the widened lookup would
-- scan the table once per bill in every settlement. This adds it.
-- ============================================================================

SET @sql := (
  SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME   = 'account_receivables'
               AND INDEX_NAME   = 'ihp_acc_rec_bill_no'),
    'SELECT ''ihp_acc_rec_bill_no already present'' AS note',
    'CREATE INDEX ihp_acc_rec_bill_no ON account_receivables (BILL_NO)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- Show what is still owed on each bill of a receivables receipt
-- ============================================================================
--   mysql -u root -p ihealthpharm < backend/database/credit_allocation_receipt_owing_column.sql
--
-- The receipt listed each bill and what was paid against it, but not what is
-- left on it, so a customer could not tell from the receipt whether a bill was
-- cleared or merely reduced.
--
-- The figure was already being fetched - ACCOUNT_RECEIVABLES_RECEIPT selects
-- ar.AMOUNT_TO_BE_RECEIVED - it simply was not among the columns the receipt
-- prints. Those columns are configuration, held as JSON in
-- reports_mapping.REPORT_HEADER, so this adds one entry rather than changing
-- the report code.
--
-- This is the receipt the Receipts screen prints after a bulk payment as well
-- as the one credit settlement prints, so both gain the column. That is
-- deliberate and was asked for: the same document either way.
--
-- AccountReceivablesReceiptPdf sizes the table from a fixed widths array, so
-- the Java change that widens it to seven columns must ship with this. Without
-- it the report throws on a column count mismatch.
--
-- Idempotent: does nothing if the column is already configured.
-- ============================================================================

UPDATE `reports_mapping`
SET `REPORT_HEADER` = '[{"displayName":"S NO","columnName":"S_NO","position":1},{"displayName":"REC NO","columnName":"RECEIPT_NO","position":2},{"displayName":"BILL NO","columnName":"SOURCE_REF","position":3},{"displayName":"REC DT","columnName":"RECEIPT_DATE","position":4},{"displayName":"AMT REC","columnName":"AMOUNT_RECEIVED","position":5},{"displayName":"OWING","columnName":"AMOUNT_TO_BE_RECEIVED","position":6},{"displayName":" TYPE","columnName":"TYPE","position":7}]'
WHERE `REPORT_CODE` = 'ACCOUNT_RECEIVABLES_RECEIPT'
  AND `REPORT_HEADER` NOT LIKE '%AMOUNT_TO_BE_RECEIVED%';

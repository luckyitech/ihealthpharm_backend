-- ============================================================================
-- Credit settlement - race-free batch references
-- ============================================================================
--   mysql -u root -p ihealthpharm < backend/database/credit_allocation_batch_ref_sequence.sql
--
-- The batch reference was built as COUNT(*) + 1 over the day's existing
-- references. Two people settling at the same moment both read the same count
-- and both build the same reference; BATCH_REF is UNIQUE, so the second one
-- failed on a raw constraint violation instead of getting its own number.
--
-- The counter now lives in generateuniquecode, the table the rest of the
-- system already numbers its documents from (SB, AR, GL, CN...), and is
-- incremented with a single atomic UPDATE so two callers cannot read the same
-- value. Note this is NOT the same as calling UniqueCodeService: that reads,
-- adds one and saves, with no lock and no @Version, so it has the very race
-- this is fixing. Only the row is shared, not the code path.
--
-- The counter runs continuously rather than resetting daily, which is how
-- every other code in this table behaves - DPAR, DPSB and DPGL all carry a
-- running number after the date.
-- ============================================================================

INSERT INTO `generateuniquecode` (`UNIQUE_CODE_NAME`, `UNIQUE_CODE_NUMBER`, `CREATION_USER_ID`, `LAST_UPDATE_TS`)
SELECT 'CA', COALESCE((SELECT COUNT(*) FROM `credit_allocation_batch`), 0), 'system', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `generateuniquecode` g WHERE g.UNIQUE_CODE_NAME = 'CA'
);

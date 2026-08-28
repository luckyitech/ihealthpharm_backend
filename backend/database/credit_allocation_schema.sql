-- ============================================================================
-- Credit payment allocation - schema
-- ============================================================================
-- Records which bills a credit payment settled, so an allocation can be
-- reported on and, when a cheque bounces, unwound exactly.
--
-- Today the only link between one payment and the several bills it clears is
-- PAY_REF_CREDIT_NO, a comma-separated string. That cannot be reversed
-- reliably, so allocation gets its own tables here.
--
--   mysql -u root -p ihealthpharm < backend/database/credit_allocation_schema.sql
--
-- Money is DECIMAL rather than the FLOAT/DOUBLE used by the older tables:
-- allocation arithmetic has to sum back to the payment exactly, and binary
-- floating point cannot guarantee that.
-- ============================================================================


-- ---------------------------------------------------------------------------
-- One row per payment allocated against a customer's outstanding bills.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `credit_allocation_batch` (
  `ALLOCATION_BATCH_ID`   int             NOT NULL AUTO_INCREMENT,
  `BATCH_REF`             varchar(30)     NOT NULL COMMENT 'Human readable ref, e.g. DPCA2808260001',

  `CUSTOMER_ID`           int             DEFAULT NULL,
  `MASTER_ACCOUNT_NO`     varchar(45)     DEFAULT NULL COMMENT 'Credit account code, e.g. DPCR2109200005 - how account_receivables identifies the account',
  `MASTER_ACCOUNT_ID`     int             DEFAULT NULL COMMENT 'Numeric id of the same account, where known',
  `PHARMACY_ID`           int             DEFAULT NULL,

  `PAYMENT_AMOUNT`        decimal(15,2)   NOT NULL COMMENT 'What the customer handed over',
  `ALLOCATED_AMOUNT`      decimal(15,2)   NOT NULL DEFAULT 0.00 COMMENT 'Sum of the lines',
  `UNALLOCATED_AMOUNT`    decimal(15,2)   NOT NULL DEFAULT 0.00 COMMENT 'Remainder when payment exceeded all bills',

  `ALLOCATION_ORDER`      varchar(10)     NOT NULL DEFAULT 'OLDEST' COMMENT 'OLDEST | NEWEST',
  `ORDER_CONFIRMED_S`     char(1)         DEFAULT 'N' COMMENT 'Y when the user explicitly chose the order (bills older than the ageing threshold)',

  `PAYMENT_TYPE`          varchar(20)     DEFAULT NULL COMMENT 'Cash | Card | MPesa | Cheque',
  `PAYMENT_REF`           varchar(50)     DEFAULT NULL COMMENT 'Cheque no, M-Pesa code, card auth code',
  `CHEQUE_ID`             int             DEFAULT NULL COMMENT 'Set for cheques - allocation waits on approval',

  `STATUS`                varchar(30)     NOT NULL DEFAULT 'ALLOCATED'
                                          COMMENT 'HELD_PENDING_APPROVAL | ALLOCATED | REVERSED | REVERSAL_FLAGGED',

  `OVERPAYMENT_ACTION`    varchar(20)     DEFAULT NULL COMMENT 'REFUND | HOLD_ON_ACCOUNT - chosen by the user',

  `REVERSES_BATCH_ID`     int             DEFAULT NULL COMMENT 'Set when this batch reverses an earlier one',
  `REVERSAL_REASON`       varchar(200)    DEFAULT NULL,

  `APPROVED_BY`           int             DEFAULT NULL,
  `APPROVED_DATE`         datetime        DEFAULT NULL,

  `BACKFILL_S`            char(1)         DEFAULT 'N' COMMENT 'Y for historical payments loaded from the EOD records',

  -- audit columns, matching AuditModel
  `CREATION_TS`           timestamp       NULL DEFAULT NULL,
  `CREATION_USER_ID`      varchar(50)     DEFAULT NULL,
  `LAST_UPDATE_TS`        timestamp       NULL DEFAULT NULL,
  `LAST_UPDATE_USER_ID`   int             DEFAULT NULL,
  `AUDIT_ID`              int             DEFAULT NULL,
  `ACTIVE_S`              char(1)         DEFAULT 'Y',

  PRIMARY KEY (`ALLOCATION_BATCH_ID`),
  UNIQUE KEY `UK_CREDIT_ALLOC_BATCH_REF` (`BATCH_REF`),
  KEY `IDX_CREDIT_ALLOC_BATCH_CUSTOMER` (`CUSTOMER_ID`),
  KEY `IDX_CREDIT_ALLOC_BATCH_MASTER_ACC` (`MASTER_ACCOUNT_ID`),
  KEY `IDX_CREDIT_ALLOC_BATCH_MASTER_ACC_NO` (`MASTER_ACCOUNT_NO`),
  KEY `IDX_CREDIT_ALLOC_BATCH_STATUS` (`STATUS`),
  KEY `IDX_CREDIT_ALLOC_BATCH_CHEQUE` (`CHEQUE_ID`),
  KEY `IDX_CREDIT_ALLOC_BATCH_CREATED` (`CREATION_TS`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------------
-- One row per bill the payment touched. PRIOR_* columns are what make a
-- reversal exact rather than a recalculation.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `credit_allocation_line` (
  `ALLOCATION_LINE_ID`        int           NOT NULL AUTO_INCREMENT,
  `ALLOCATION_BATCH_ID`       int           NOT NULL,
  `SEQUENCE_NO`               int           NOT NULL COMMENT 'Order the bill was filled within the batch',

  `ACCOUNT_RECEIVABLES_ID`    int           DEFAULT NULL,
  `SALES_BILL_ID`             int           DEFAULT NULL,
  `BILL_CODE`                 varchar(30)   DEFAULT NULL,
  `BILL_DATE`                 date          DEFAULT NULL COMMENT 'Denormalised - this is what the ordering sorted on',

  `AMOUNT_APPLIED`            decimal(15,2) NOT NULL,

  `PRIOR_BALANCE`             decimal(15,2) DEFAULT NULL COMMENT 'Balance before this batch, for reversal',
  `PRIOR_PAYMENT_STATUS`      varchar(20)   DEFAULT NULL COMMENT 'Status before this batch, for reversal',
  `RESULTING_PAYMENT_STATUS`  varchar(20)   DEFAULT NULL COMMENT 'Paid | Partially Paid',

  -- audit columns, matching AuditModel
  `CREATION_TS`               timestamp     NULL DEFAULT NULL,
  `CREATION_USER_ID`          varchar(50)   DEFAULT NULL,
  `LAST_UPDATE_TS`            timestamp     NULL DEFAULT NULL,
  `LAST_UPDATE_USER_ID`       int           DEFAULT NULL,
  `AUDIT_ID`                  int           DEFAULT NULL,
  `ACTIVE_S`                  char(1)       DEFAULT 'Y',

  PRIMARY KEY (`ALLOCATION_LINE_ID`),
  KEY `IDX_CREDIT_ALLOC_LINE_BATCH` (`ALLOCATION_BATCH_ID`),
  KEY `IDX_CREDIT_ALLOC_LINE_BILL` (`SALES_BILL_ID`),
  KEY `IDX_CREDIT_ALLOC_LINE_RECEIVABLE` (`ACCOUNT_RECEIVABLES_ID`),
  CONSTRAINT `FK_CREDIT_ALLOC_LINE_BATCH`
    FOREIGN KEY (`ALLOCATION_BATCH_ID`) REFERENCES `credit_allocation_batch` (`ALLOCATION_BATCH_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------------
-- Permissions. Granted through the existing Master > Employee screen, which
-- writes to employee_access.
-- ---------------------------------------------------------------------------
INSERT INTO `pharma_access` (`ACCESS_CD`, `ACCESS_NAME`, `CREATION_TS`, `CREATION_USER_ID`, `ACTIVE_S`)
SELECT * FROM (
  SELECT 'Sales' AS a, 'Credit Settlement' AS b, NOW() AS c, 'system' AS d, 'Y' AS e
  UNION ALL
  SELECT 'Sales', 'Credit Settlement Reversal', NOW(), 'system', 'Y'
) AS new_access
WHERE NOT EXISTS (
  SELECT 1 FROM `pharma_access` p
  WHERE p.ACCESS_CD = new_access.a AND p.ACCESS_NAME = new_access.b
);

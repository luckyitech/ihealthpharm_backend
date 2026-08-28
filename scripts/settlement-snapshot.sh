# Complete rows, every column - no hand-picked list to have blind spots in.
mysql -u root -proot ihealthpharm -N -B -e "
SELECT 'AR', ar.* FROM account_receivables ar
 WHERE ar.ACCOUNT_RECEIVABLES_ID IN (127399,127638,127662);" 2>/dev/null
mysql -u root -proot ihealthpharm -N -B -e "
SELECT 'SALES', s.* FROM sales s
 WHERE s.BILL_CODE IN ('DPSB1705210018','DPSB2906210152','DPSB3006210099');" 2>/dev/null
mysql -u root -proot ihealthpharm -N -B -e "
SELECT 'MA', m.* FROM master_account m WHERE m.CREDIT_NUMBER='DPCR2109200005';" 2>/dev/null

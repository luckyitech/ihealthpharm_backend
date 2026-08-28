# Verification scripts

## settlement-snapshot.sh

Dumps complete rows - every column - for the records a credit settlement
touches: the receivables, their sales bills, and the credit account.

Run it before and after a settle/reverse cycle and diff the two. Ignore
LAST_UPDATE_TS and LAST_UPDATE_USER_ID, which are meant to move.

    scripts/settlement-snapshot.sh > before.txt
    ...settle, approve, reverse...
    scripts/settlement-snapshot.sh > after.txt
    diff before.txt after.txt

Use this rather than picking out the columns that seem relevant. Three
separate bugs in this feature survived hand-picked checks and were only
caught once whole rows were compared - the sales bill's cheque date was
the last of them, missed by four rounds of column-by-column verification.

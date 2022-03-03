
SELECT *
FROM ${tablename}
WHERE id = (
 SELECT parent
 FROM ${tablename}
 WHERE id = ?
 LIMIT 1
) AND id != 1 -- do not allow that ROOT is returned
LIMIT 1

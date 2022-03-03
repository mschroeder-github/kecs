
SELECT *
FROM "${tablename}"
WHERE parent = ? AND type = ?
ORDER BY sort

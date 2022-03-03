
WITH RECURSIVE temp (tid) AS
(
  -- start
  SELECT id
  FROM "${tablename}"
  WHERE id = ?

  UNION ALL

  -- recursive
  SELECT "${tablename}".id
  FROM temp
  JOIN "${tablename}" ON "${tablename}".parent = temp.tid -- get children
)
SELECT "${tablename}".*
FROM "${tablename}"
JOIN temp ON temp.tid = "${tablename}".id
WHERE "${tablename}".id != 1;

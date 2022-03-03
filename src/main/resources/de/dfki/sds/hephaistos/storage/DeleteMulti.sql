
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
DELETE 
FROM "${tablename}"
WHERE id != 1 AND "${tablename}".id IN (SELECT * FROM temp);


WITH RECURSIVE temp (tid) AS
(
  -- start
  SELECT parent
  FROM ${tablename}
  WHERE id = ?

  UNION ALL

  -- recursive
  SELECT ${tablename}.parent
  FROM temp
  JOIN ${tablename} ON ${tablename}.id = temp.tid -- get parent
)
SELECT ${tablename}.*
FROM ${tablename} 
JOIN temp ON ${tablename}.id = temp.tid;
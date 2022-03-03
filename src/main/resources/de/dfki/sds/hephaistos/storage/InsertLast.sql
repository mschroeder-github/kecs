
INSERT INTO ${tablename}
SELECT NULL, ?,
(SELECT CASE WHEN EXISTS(SELECT id FROM ${tablename} WHERE parent = ?) THEN (SELECT sort FROM ${tablename} WHERE parent = ? ORDER BY "sort" DESC LIMIT 1) ELSE 0 END) + 1
, ?, ${params}
;
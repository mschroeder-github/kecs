
INSERT INTO "${tablename}" VALUES (
	?, -- "s" TEXT,
	?, -- "p" TEXT,
	?, -- "o" TEXT,
    ?, -- "phase" TEXT,

    ?, -- "ai_intelligence" TEXT,
    ?, -- "ai_name" TEXT,
    ?, -- "ai_rating" TEXT,
    ?, -- "ai_confidence" REAL,
    ?, -- "ai_when" INTEGER,

    ?, -- "ni_intelligence" TEXT,
    ?, -- "ni_name" TEXT,
    ?, -- "ni_rating" TEXT,
    ?, -- "ni_confidence" REAL,
    ?  -- "ni_when" INTEGER,
)
ON CONFLICT(s, p, o) DO UPDATE SET
    ${prefix}intelligence = ?,
    ${prefix}name = ?,
    ${prefix}rating = ?,
    ${prefix}confidence = ?,
    ${prefix}when = ? ;


package de.dfki.sds.hephaistos.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.function.Function;

/**
 * To iterate over a result set (sql context) and transform the result set to a
 * specific type.
 *
 * 
 */
public class ResultSetIterator<T> implements Iterator<T> {

    private boolean didNext = false;
    private boolean hasNext = false;

    private PreparedStatement stmt;
    private ResultSet rs;
    private Function<ResultSet, T> rs2t;

    public ResultSetIterator(PreparedStatement stmt, Function<ResultSet, T> rs2t) {
        this.stmt = stmt;
        this.rs2t = rs2t;
        try {
            this.rs = stmt.executeQuery();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void checkHasNext(boolean setDidNextToTrue) {
        if (!didNext) {
            try {
                hasNext = rs.next();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            if (setDidNextToTrue) {
                didNext = true;
            }
        }
        if (!setDidNextToTrue) {
            didNext = false;
        }
    }

    @Override
    public boolean hasNext() {
        checkHasNext(true);
        if (!hasNext) {
            close();
        }
        return hasNext;
    }

    @Override
    public T next() {
        checkHasNext(false);
        try {
            return rs2t.apply(rs);
        } catch(Exception e) {
            close();
            throw e;
        }
    }

    public PreparedStatement getPreparedStatement() {
        return stmt;
    }
    
    public void close() {
        try {
            stmt.close();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
    
}

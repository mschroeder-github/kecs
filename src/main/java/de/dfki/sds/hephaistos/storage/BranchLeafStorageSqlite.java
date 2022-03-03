package de.dfki.sds.hephaistos.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import org.apache.commons.io.IOUtils;

/**
 * Sqlite implementation for branch leaf hierarchical storage.
 *
 * 
 */
public abstract class BranchLeafStorageSqlite<Branch extends StorageItem, Leaf extends StorageItem, S extends StorageSummary> extends BranchLeafStorage<Branch, Leaf, S, ResultSet> {

    protected final String COL_ID = "id";
    protected final String COL_PARENT = "parent";
    protected final String COL_SORT = "sort";
    protected final String COL_TYPE = "type";
    protected List<TypedName> nodeSchema = Arrays.asList(
            new TypedName(COL_ID, Integer.class),
            new TypedName(COL_PARENT, Integer.class),
            new TypedName(COL_SORT, Integer.class),
            new TypedName(COL_TYPE, Integer.class)
    );

    protected final int ROOT_ID = 1;
    protected final int BRANCH_TYPE = 0;
    protected final int LEAF_TYPE = 1;

    private String tablename;
    private Connection connection;

    public BranchLeafStorageSqlite(InternalStorageMetaData metaData, Connection connection) {
        super(metaData);
        this.tablename = metaData.getId();
        this.connection = connection;
        init();
    }

    //to create table
    protected abstract List<TypedName> getBranchSchema();

    protected abstract List<TypedName> getLeafSchema();

    //to insert
    protected abstract Object[] getBranchInsertParams(Branch branch);

    protected abstract Object[] getLeafInsertParams(Leaf leaf);

    //to perform class check
    protected abstract Class<Branch> getBranchClass();

    protected abstract Class<Leaf> getLeafClass();

    //to create from result set
    protected abstract Branch getBranchFromRow(Row rs);

    protected abstract Leaf getLeafFromRow(Row rs);

    //to set/get id, parent, sort, type
    protected abstract MetaData getBranchMetaData(Branch branch);

    protected abstract MetaData getLeafMetaData(Leaf leaf);

    protected abstract void setBranchMetaData(Branch branch, MetaData metaData);

    protected abstract void setLeafMetaData(Leaf leaf, MetaData metaData);
    
    
    //optional decide to insert/get/remove something additionally
    //only for single leaf operations
    
    protected void insertAdditionally(Leaf leaf) {
        
    }
    
    protected void retrieveAdditionally(Leaf leaf) {
        
    }
    
    protected void removeAdditionally(Leaf leaf) {
        
    }

    /**
     * This class is used to read and write the tree meta data.
     */
    protected class MetaData {

        private int id;
        private int parent;
        private int sort;
        private int type;
        private boolean typeSet;

        public MetaData() {
        }

        public MetaData(int id, int parent, int sort) {
            this.id = id;
            this.parent = parent;
            this.sort = sort;
        }

        public MetaData(int id, int parent, int sort, int type) {
            this.id = id;
            this.parent = parent;
            this.sort = sort;
            this.type = type;
            this.typeSet = true;
        }

        public MetaData(ResultSet rs) {
            try {
                id = rs.getInt(COL_ID);
                parent = rs.getInt(COL_PARENT);
                sort = rs.getInt(COL_SORT);
                type = rs.getInt(COL_TYPE);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getParent() {
            return parent;
        }

        public void setParent(int parent) {
            this.parent = parent;
        }

        public int getSort() {
            return sort;
        }

        public void setSort(int sort) {
            this.sort = sort;
        }

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
            typeSet = true;
        }

        private boolean isTypeSet() {
            return typeSet;
        }

        @Override
        public String toString() {
            return "MetaData{" + "id=" + id + ", parent=" + parent + ", sort=" + sort + '}';
        }

    }

    /**
     * This class is used to pass the row to fill the node's attributes.
     */
    protected class Row {

        private ResultSet rs;
        private int offset;

        public Row(ResultSet rs, int type) {
            this.rs = rs;
            this.offset = type == LEAF_TYPE ? getBranchSchema().size() : 0;
        }

        public int getInt(int index) {
            try {
                return rs.getInt(nodeSchema.size() + offset + index);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }

        public long getLong(int index) {
            try {
                return rs.getLong(nodeSchema.size() + offset + index);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }

        public String getString(int index) {
            try {
                return rs.getString(nodeSchema.size() + offset + index);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }

        public double getDouble(int index) {
            try {
                return rs.getDouble(nodeSchema.size() + offset + index);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }

        public float getFloat(int index) {
            try {
                return rs.getFloat(nodeSchema.size() + offset + index);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }

        public boolean getBoolean(int index) {
            try {
                return rs.getBoolean(nodeSchema.size() + offset + index);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Create one table if not exists based on List TypedName of both.
     */
    private void init() {
        SQLiteUtility.run(connection, c -> {
            String createTableQuery = getCreateTableQuery();
            c.prepareStatement(createTableQuery).execute();

            insertRoot(c);
        });
    }

    private List<TypedName> getAllTypeNames() {
        List<TypedName> allTypedNames = new ArrayList<>();
        allTypedNames.addAll(nodeSchema);
        allTypedNames.addAll(getBranchLeafTypeNames());
        return allTypedNames;
    }

    private List<TypedName> getBranchLeafTypeNames() {
        List<TypedName> allTypedNames = new ArrayList<>();
        for (TypedName tn : getBranchSchema()) {
            allTypedNames.add(tn.withPrefix("branch_"));
        }
        for (TypedName tn : getLeafSchema()) {
            allTypedNames.add(tn.withPrefix("leaf_"));
        }
        return allTypedNames;
    }

    private String getCreateTableQuery() {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS \"" + tablename + "\" (");

        String notNullPrimary = "NOT NULL PRIMARY KEY AUTOINCREMENT";

        //sb.append("\""+ COL_ID +"\"	INTEGER ,\n");
        //sb.append("\""+ COL_PARENT +"\"	INTEGER,\n");
        //sb.append("\""+ COL_TYPE +"\"	INTEGER\n");
        boolean first = true;
        for (TypedName typedName : getAllTypeNames()) {
            if (!first) {
                sb.append(", ");
            }

            sb.append("\"" + typedName.getName() + "\"	" + getSqliteType(typedName.getType()));

            if (first) {
                sb.append(" " + notNullPrimary);
                first = false;
            }
            sb.append("\n");
        }

        sb.append(");");
        return sb.toString();
    }

    private String getClearQuery() {
        return "DELETE FROM \"" + tablename + "\" WHERE id != 1;";
    }

    private String getRootQuery() {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT *\n");
        sb.append("FROM \"" + tablename + "\"\n");
        sb.append("WHERE " + COL_ID + " = " + ROOT_ID + "\n");
        sb.append("LIMIT 1;");
        return sb.toString();
    }
    
    private String getSelectQuery(int id) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT *\n");
        sb.append("FROM \"" + tablename + "\"\n");
        sb.append("WHERE " + COL_ID + " = " + id + "\n");
        sb.append("LIMIT 1;");
        return sb.toString();
    }
    
    private String getSelectIdQuery() {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT id\n");
        sb.append("FROM \"" + tablename + "\"");
        return sb.toString();
    }

    private String getInsertIntoValuesQuestionmarks(boolean orIgnore) {
        return "INSERT " + (orIgnore ? "OR IGNORE " : "") + "INTO \"" + tablename + "\" VALUES ("
                + getQuestionmarks(getAllTypeNames().size())
                + ");";
    }

    private void insertRoot(Connection c) throws SQLException {
        String insertRootQuery = getInsertIntoValuesQuestionmarks(true);
        PreparedStatement ps = c.prepareStatement(insertRootQuery);
        setParametersNull(ps);
        ps.setInt(1, ROOT_ID); //id
        ps.setInt(2, 0); //parent (0 to have an end for recursion)
        ps.setInt(3, 0); //sort
        ps.setInt(4, BRANCH_TYPE); //type
        ps.execute();
    }

    private String getQuery(String path) {
        try {
            String query = IOUtils.toString(BranchLeafStorageSqlite.class.getResourceAsStream(path), StandardCharsets.UTF_8);
            query = query.replaceAll("\\$\\{tablename\\}", tablename);
            return query;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private PreparedStatement getChildrenPreparedStatement(Branch node, int type) {
        return SQLiteUtility.prepare(connection, getQuery("/de/dfki/sds/hephaistos/storage/Children.sql"), ps -> {
            ps.setInt(1, getBranchMetaDataInner(node).getId());
            ps.setInt(2, type);
        });
    }

    /**
     * Uses class to determine branch or leaf.
     *
     * @param node
     * @return
     */
    private MetaData getMetaData(StorageItem node) {
        MetaData md;

        Class subclass = node.getClass();
        
        if (getBranchClass().isAssignableFrom(subclass)) {
            md = getBranchMetaDataInner((Branch) node);
        } else if (getLeafClass().isAssignableFrom(subclass)) {
            md = getLeafMetaDataInner((Leaf) node);
        } else {
            throw new RuntimeException(node.getClass() + " is neither " + getBranchClass() + " nor " + getLeafClass());
        }

        return md;
    }

    private MetaData getBranchMetaDataInner(Branch branch) {
        MetaData md = getBranchMetaData(branch);
        //if type is not set yet it will be set 
        if (!md.isTypeSet()) {
            md.setType(BRANCH_TYPE);
        }
        return md;
    }

    private MetaData getLeafMetaDataInner(Leaf leaf) {
        MetaData md = getLeafMetaData(leaf);
        //if type is not set yet it will be set 
        if (!md.isTypeSet()) {
            md.setType(LEAF_TYPE);
        }
        return md;
    }

    private PreparedStatement getParentsPreparedStatement(StorageItem node) {
        MetaData md = getMetaData(node);
        return SQLiteUtility.prepare(connection, getQuery("/de/dfki/sds/hephaistos/storage/Parents.sql"), ps -> {
            ps.setInt(1, md.getId());
        });
    }

    private PreparedStatement getParentPreparedStatement(StorageItem node) {
        MetaData md = getMetaData(node);
        return SQLiteUtility.prepare(connection, getQuery("/de/dfki/sds/hephaistos/storage/Parent.sql"), ps -> {
            ps.setInt(1, md.getId());
        });
    }

    private PreparedStatement getTreePreparedStatement(Branch node) {
        return SQLiteUtility.prepare(connection, getQuery("/de/dfki/sds/hephaistos/storage/SelectTree.sql"), ps -> {
            ps.setInt(1, getBranchMetaDataInner(node).getId());
        });
    }

    private Branch getBranchFromResultSet(ResultSet rs) {
        Branch b = getBranchFromRow(new Row(rs, BRANCH_TYPE));
        setBranchMetaData(b, new MetaData(rs));
        return b;
    }

    private Leaf getLeafFromResultSet(ResultSet rs) {
        Leaf l = getLeafFromRow(new Row(rs, LEAF_TYPE));
        setLeafMetaData(l, new MetaData(rs));
        retrieveAdditionally(l);
        return l;
    }

    //sqlite helper 
    private String getQuestionmarks(int n) {
        StringJoiner sj = new StringJoiner(",");
        for (int i = 0; i < n; i++) {
            sj.add("?");
        }
        return sj.toString();
    }

    private String getSqliteType(Class type) {
        if (type == Integer.class || type == Long.class || type == Boolean.class) {
            return "INTEGER";
        } else if (type == String.class || type == Character.class) {
            return "TEXT";
        } else if (type == Float.class || type == Double.class) {
            return "REAL";
        } else {
            throw new RuntimeException(type + " not supported");
        }
    }

    private void setParametersNull(PreparedStatement ps) throws SQLException {
        for (int i = 0; i < ps.getParameterMetaData().getParameterCount(); i++) {
            if (ps.getParameterMetaData().isNullable(i + 1) == ParameterMetaData.parameterNullable) {
                ps.setNull(i + 1, Types.OTHER);
            }
        }
    }

    //override
    private void insert(StorageItem node, Branch parent, boolean nodeIsBranch, String firstOrLast) {

        int n = getBranchLeafTypeNames().size();
        int parentId = getBranchMetaDataInner(parent).getId();
        int branchSchemaLength = getBranchSchema().size();

        String query = getQuery("/de/dfki/sds/hephaistos/storage/Insert" + firstOrLast + ".sql").replaceAll("\\$\\{params\\}", getQuestionmarks(n));

        SQLiteUtility.run(connection, c -> {

            PreparedStatement stmt = c.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);

            setParametersNull(stmt);
            stmt.setInt(1, parentId);
            stmt.setInt(2, parentId);
            stmt.setInt(3, parentId);
            stmt.setInt(4, nodeIsBranch ? BRANCH_TYPE : LEAF_TYPE);

            Object[] values;
            if (nodeIsBranch) {
                values = getBranchInsertParams((Branch) node);
            } else {
                values = getLeafInsertParams((Leaf) node);
            }
            for (int i = 0; i < values.length; i++) {
                stmt.setObject(5 + (nodeIsBranch ? 0 : branchSchemaLength) + i, values[i]);
            }

            stmt.execute();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                MetaData md = new MetaData();
                if (generatedKeys.next()) {
                    md.setId(generatedKeys.getInt(1));
                    md.setParent(parentId);
                    if (nodeIsBranch) {
                        setBranchMetaData((Branch) node, md);
                    } else {
                        setLeafMetaData((Leaf) node, md);
                    }
                } else {
                    throw new SQLException("No ID generated");
                }
            }
            stmt.close();
        });
        
        if(!nodeIsBranch) {
            insertAdditionally((Leaf) node);
        }
    }

    /**
     * Inserts a branch node to the parent's children as first child.
     *
     * @param node we need the node's attributes. After this call the node's
     * meta data (id, parent, sort, type) is updated.
     * @param parent we need the parent's id
     */
    @Override
    public void insertBranchAsFirstChild(Branch node, Branch parent) {
        insert(node, parent, true, "First");
    }

    /**
     * Inserts a branch node to the parent's children as last child.
     *
     * @param node we need the node's attributes. After this call the node's
     * meta data (id, parent, sort, type) is updated.
     * @param parent we need the parent's id
     */
    @Override
    public void insertBranchAsLastChild(Branch node, Branch parent) {
        insert(node, parent, true, "Last");
    }

    /**
     * Inserts a leaf node to the parent's children as first child.
     *
     * @param node we need the node's attributes. After this call the node's
     * meta data (id, parent, sort, type) is updated.
     * @param parent we need the parent's id
     */
    @Override
    public void insertLeafAsFirstChild(Leaf node, Branch parent) {
        insert(node, parent, false, "First");
    }

    /**
     * Inserts a leaf node to the parent's children as last child.
     *
     * @param node we need the node's attributes. After this call the node's
     * meta data (id, parent, sort, type) is updated.
     * @param parent we need the parent's id
     */
    @Override
    public void insertLeafAsLastChild(Leaf node, Branch parent) {
        insert(node, parent, false, "Last");
    }

    @Override
    public void insertBulk(Collection<? extends StorageItem> tree) {

        int n = getBranchLeafTypeNames().size();
        int branchSchemaLength = getBranchSchema().size();

        String query = getQuery("/de/dfki/sds/hephaistos/storage/Insert.sql").replaceAll("\\$\\{params\\}", getQuestionmarks(n));

        SQLiteUtility.run(connection, c -> {
            c.setAutoCommit(false);
            PreparedStatement stmt = c.prepareStatement(query);

            for (StorageItem node : tree) {

                MetaData md = getMetaData(node);
                Object[] values;
                int offset;

                if (md.type == BRANCH_TYPE) {
                    Branch b = (Branch) node;
                    values = getBranchInsertParams(b);
                    offset = 0;
                } else if (md.type == LEAF_TYPE) {
                    Leaf l = (Leaf) node;
                    insertAdditionally(l);
                    values = getLeafInsertParams(l);
                    offset = branchSchemaLength;
                } else {
                    throw new RuntimeException(md.type + " type not allowed");
                }

                setParametersNull(stmt);

                stmt.setInt(1, md.id);
                stmt.setInt(2, md.parent);
                stmt.setInt(3, md.sort);
                stmt.setInt(4, md.type);

                for (int i = 0; i < values.length; i++) {
                    stmt.setObject(nodeSchema.size() + 1 + offset + i, values[i]);
                }

                stmt.addBatch();
                
                
            }

            stmt.executeBatch();
            c.commit();
        });
    }

    //TODO updateBranch
    @Override
    public void updateBranch(Branch node) {
        throw new RuntimeException("not implemented yet");
    }

    //TODO updateLeaf
    @Override
    public void updateLeaf(Leaf node) {
        throw new RuntimeException("not implemented yet");
    }

    //TODO removeSingleBranch
    @Override
    public void removeSingleBranch(Branch node) {
        throw new RuntimeException("not implemented yet");
    }

    /**
     * Removes the leaf node.
     *
     * @param node we need the node's id.
     */
    @Override
    public void removeSingleLeaf(Leaf node) {
        SQLiteUtility.executePrepared(connection, getQuery("/de/dfki/sds/hephaistos/storage/DeleteSingle.sql"), ps -> {
            ps.setInt(1, getLeafMetaDataInner(node).getId());
        });
        removeAdditionally(node);
    }

    /**
     * Remove the node and its decendents.
     *
     * @param node we need the node's id.
     */
    @Override
    public void removeSubtree(Branch node) {
        SQLiteUtility.executePrepared(connection, getQuery("/de/dfki/sds/hephaistos/storage/DeleteMulti.sql"), ps -> {
            ps.setInt(1, getBranchMetaDataInner(node).getId());
        });
        //TODO for all leaf we should delete the content too with removeAddtionally
    }

    /**
     * Returns the root with id = 1.
     *
     * @return
     */
    @Override
    public Branch getRoot() {
        return SQLiteUtility.supply(connection, c -> {
            PreparedStatement ps = c.prepareStatement(getRootQuery());
            ResultSet rs = ps.executeQuery();
            Branch root;
            if (rs.next()) {
                root = getBranchFromResultSet(rs);
            } else {
                ps.close();
                throw new SQLException("root not found");
            }
            ps.close();
            return root;
        });
    }
    
    @Override
    public StorageItem get(int id) {
        return SQLiteUtility.supply(connection, c -> {
            PreparedStatement ps = c.prepareStatement(getSelectQuery(id));
            ResultSet rs = ps.executeQuery();
            StorageItem item = null;
            if (rs.next()) {
                
                if(rs.getInt(COL_TYPE) == BRANCH_TYPE) {
                    item = getBranchFromResultSet(rs);
                } else if(rs.getInt(COL_TYPE) == LEAF_TYPE) {
                    item = getLeafFromResultSet(rs);
                }
            } else {
                ps.close();
                throw new SQLException("item not found with id " + id);
            }
            ps.close();
            return item;
        });
        
    }

    /**
     * Iterates over branch children given a node's id.
     *
     * @param node we need the node's id.
     * @return
     */
    @Override
    public Iterable<Branch> getBranchChildrenIter(Branch node) {
        return () -> {
            PreparedStatement ps = getChildrenPreparedStatement(node, BRANCH_TYPE);
            return new ResultSetIterator<>(ps, rs -> getBranchFromResultSet(rs));
        };
    }

    /**
     * Iterates over leaf children given a node's id.
     *
     * @param node we need the node's id.
     * @return
     */
    @Override
    public Iterable<Leaf> getLeafChildrenIter(Branch node) {
        return () -> {
            PreparedStatement ps = getChildrenPreparedStatement(node, LEAF_TYPE);
            return new ResultSetIterator<>(ps, rs -> getLeafFromResultSet(rs));
        };
    }

    /**
     * Returns the parent of given branch of leaf.
     *
     * @param branchOrLeaf only need id set.
     * @return optional fully filled branch
     */
    @Override
    public Optional<Branch> getParentOf(StorageItem branchOrLeaf) {
        PreparedStatement ps = getParentPreparedStatement(branchOrLeaf);

        try {
            ResultSet rs = ps.executeQuery();
            
            if (!rs.next()) {
                return Optional.empty();
            }

            Branch b = getBranchFromResultSet(rs);

            ps.close();

            return Optional.of(b);

        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Iterates over parents from node's parent to root.
     *
     * @param branchOrLeaf we need the node's id.
     * @return
     */
    @Override
    public Iterable<Branch> getParentsIter(StorageItem branchOrLeaf) {
        return () -> {
            PreparedStatement ps = getParentsPreparedStatement(branchOrLeaf);
            return new ResultSetIterator<>(ps, rs -> {
                return getBranchFromResultSet(rs);
            });
        };
    }

    @Override
    public Iterable<StorageItem> getTreeIter(Branch node) {
        return () -> {
            PreparedStatement ps = getTreePreparedStatement(node);
            return new ResultSetIterator<>(ps, rs -> {
                try {
                    int type = rs.getInt(COL_TYPE);
                    if (type == BRANCH_TYPE) {
                        Branch o = getBranchFromResultSet(rs);
                        return o;
                    } else if (type == LEAF_TYPE) {
                        Leaf o = getLeafFromResultSet(rs);
                        retrieveAdditionally(o);
                        return o;
                    } else {
                        throw new SQLException("Illegal type " + type);
                    }
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });
        };
    }

    @Override
    public void clear() {
        SQLiteUtility.execute(connection, getClearQuery());
    }

    @Override
    public void remove() {
        SQLiteUtility.execute(connection, getQuery("/de/dfki/sds/hephaistos/storage/Drop.sql"));
    }

    //TODO search
    /*
    @Override
    public List<StorageItem> search(String keywords) {
        throw new RuntimeException("not implemented yet");
    }
    */
    
    /*
    @Override
    public ResultSet query(String query) {
        String q = query.replaceAll("\\$\\{tablename\\}", tablename);
        return SQLiteUtility.supply(connection, c -> {
            return c.prepareStatement(q).executeQuery();
        });
        //close it with rs.getStatement().close();
    }
    */
    
    /*
    @Override
    public void execute(String query) {
        query = query.replaceAll("\\$\\{tablename\\}", tablename);
        SQLiteUtility.execute(connection, query);
    }
    */
    
    @Override
    public long size() {
        String query = getQuery("/de/dfki/sds/hephaistos/storage/Count.sql");
        return SQLiteUtility.supply(connection, c -> {
            PreparedStatement stmt = c.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            long size = rs.getLong(1);
            stmt.close();
            return size;
        });
    }
    
    public long getCount(int type) {
        String query = getQuery("/de/dfki/sds/hephaistos/storage/CountType.sql");
        return SQLiteUtility.supply(connection, c -> {
            PreparedStatement stmt = c.prepareStatement(query);
            stmt.setInt(1, type);
            ResultSet rs = stmt.executeQuery();
            long size = rs.getLong(1);
            stmt.close();
            return size;
        });
    }
    
    @Override
    public long getBranchCount() {
        return getCount(BRANCH_TYPE);
    }
    
    @Override
    public long getLeafCount() {
        return getCount(LEAF_TYPE);
    }

    @Override
    public void close() {
        //ignore
    }

    @Override
    public boolean isRoot(Branch node) {
        return getBranchMetaData(node).id == 1;
    }

    @Override
    public boolean isBranch(StorageItem node) {
        return getMetaData(node).type == BRANCH_TYPE;
    }

    @Override
    public boolean isLeaf(StorageItem node) {
        return getMetaData(node).type == LEAF_TYPE;
    }

}

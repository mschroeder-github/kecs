package de.dfki.sds.hephaistos.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A hierarchy with branch nodes and leaf nodes.
 * 
 */
public abstract class BranchLeafStorage
        <Branch extends StorageItem,
        Leaf extends StorageItem,
        S extends StorageSummary,
        RS>
        extends
        InternalStorage<StorageItem, S, RS>
        {
    
    public BranchLeafStorage(InternalStorageMetaData metaData) {
        super(metaData);
    }
    
    public abstract void insertBranchAsFirstChild(Branch node, Branch parent);
    public abstract void insertBranchAsLastChild(Branch node, Branch parent);
    
    public abstract void insertLeafAsFirstChild(Leaf node, Branch parent);
    public abstract void insertLeafAsLastChild(Leaf node, Branch parent);
    
    public abstract void insertBulk(Collection<? extends StorageItem> tree);
    
    public abstract void removeSingleBranch(Branch node);
    public abstract void removeSingleLeaf(Leaf node);
    
    public abstract void updateBranch(Branch node);
    public abstract void updateLeaf(Leaf node);
    
    public abstract void removeSubtree(Branch node);

    public abstract Branch getRoot();
    
    public abstract Iterable<Branch> getBranchChildrenIter(Branch node);
    public abstract Iterable<Leaf> getLeafChildrenIter(Branch node);
    
    public abstract Optional<Branch> getParentOf(StorageItem branchOrLeaf);
    public abstract Iterable<Branch> getParentsIter(StorageItem branchOrLeaf);
    
    /**
     * Iterates the node and its decendents.
     * This will never return root, even if given node is root.
     * @param node we need the node's id
     * @return 
     */
    public abstract Iterable<StorageItem> getTreeIter(Branch node);
    
    public abstract boolean isRoot(Branch node);
    public abstract boolean isBranch(StorageItem node);
    public abstract boolean isLeaf(StorageItem node);
    
    /**
     * Returns storage item by id.
     * @param id
     * @return 
     */
    public abstract StorageItem get(int id);
    
    public abstract long getBranchCount();
    public abstract long getLeafCount();

    
    /**
     * Returns the parents from node's parent to root.
     * @param node
     * @return 
     */
    public List<Branch> getParents(StorageItem node) {
        List<Branch> result = new ArrayList<>();
        getParentsIter(node).forEach(result::add);
        return result;
    }
    
    /**
     * Returns the parents from root to node's parent.
     * @param node
     * @return 
     */
    public List<Branch> getParentsInverted(StorageItem node) {
        List<Branch> l = getParents(node);
        Collections.reverse(l);
        return l;
    }
    
    /**
     * Returns the parents from root's child to node's parent.
     * @param node
     * @return 
     */
    public List<Branch> getParentsInvertedRootless(StorageItem node) {
        List<Branch> l = getParents(node);
        Collections.reverse(l);
        return l.subList(1, l.size());
    }
    
    public List<Branch> getBranchChildren(Branch node){
        List<Branch> result = new ArrayList<>();
        getBranchChildrenIter(node).forEach(result::add);
        return result;
    }
    
    public List<Leaf> getLeafChildren(Branch node){
        List<Leaf> result = new ArrayList<>();
        getLeafChildrenIter(node).forEach(result::add);
        return result;
    }
    
    /**
     * Returns in a flat list the node and its decendents.
     * This will never return root, even if given node is root.
     * @param node we need the node's id
     * @return 
     */
    public List<StorageItem> getTree(Branch node) {
        List<StorageItem> result = new ArrayList<>();
        getTreeIter(node).forEach(result::add);
        return result;
    }
}

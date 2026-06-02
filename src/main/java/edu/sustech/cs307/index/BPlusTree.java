package edu.sustech.cs307.index;

import edu.sustech.cs307.value.Value;
import edu.sustech.cs307.value.ValueComparer;

import java.util.*;

/**
 * In-memory B+ Tree implementation for indexing Value→RID mappings.
 * Supports insert, exact search, and range queries.
 */
public class BPlusTree {

    private final int order; // maximum number of children per internal node
    private Node root;
    private LeafNode firstLeaf;

    public BPlusTree(int order) {
        this.order = order;
        this.root = new LeafNode();
        this.firstLeaf = (LeafNode) this.root;
    }

    public void insert(Value key, Object value) {
        InsertResult result = insertRecursive(root, key, value);
        if (result.splitKey != null) {
            InternalNode newRoot = new InternalNode();
            newRoot.keys.add(result.splitKey);
            newRoot.children.add(root);
            newRoot.children.add(result.newNode);
            root = newRoot;
        }
    }

    public Object search(Value key) {
        LeafNode leaf = findLeaf(key);
        int idx = findKeyIndex(leaf.keys, key);
        if (idx < leaf.keys.size() && compare(leaf.keys.get(idx), key) == 0) {
            return leaf.values.get(idx).get(0); // return first value for the key
        }
        return null;
    }

    public List<Map.Entry<Value, Object>> rangeSearch(Value low, boolean lowInclusive,
                                                       Value high, boolean highInclusive) {
        List<Map.Entry<Value, Object>> results = new ArrayList<>();
        LeafNode leaf = findLeaf(low != null ? low : firstLeaf.keys.isEmpty() ? null : firstLeaf.keys.get(0));
        if (leaf == null) return results;

        while (leaf != null) {
            for (int i = 0; i < leaf.keys.size(); i++) {
                Value key = leaf.keys.get(i);
                // Check lower bound
                if (low != null) {
                    int cmpLow = compare(key, low);
                    if (cmpLow < 0 || (!lowInclusive && cmpLow == 0)) continue;
                }
                // Check upper bound
                if (high != null) {
                    int cmpHigh = compare(key, high);
                    if (cmpHigh > 0 || (!highInclusive && cmpHigh == 0)) break;
                }
                // Add all values for this key
                for (Object val : leaf.values.get(i)) {
                    results.add(new AbstractMap.SimpleEntry<>(key, val));
                }
            }
            leaf = leaf.next;
        }
        return results;
    }

    public Iterator<Map.Entry<Value, Object>> scanAll() {
        return new BPlusTreeIterator();
    }

    public boolean isEmpty() {
        return firstLeaf.keys.isEmpty();
    }

    public int size() {
        int count = 0;
        LeafNode leaf = firstLeaf;
        while (leaf != null) {
            for (List<Object> valList : leaf.values) {
                count += valList.size();
            }
            leaf = leaf.next;
        }
        return count;
    }

    // --- Internal implementation ---

    private int compare(Value a, Value b) {
        try {
            return ValueComparer.compare(a, b);
        } catch (Exception e) {
            throw new RuntimeException("B+ Tree comparison failed", e);
        }
    }

    private LeafNode findLeaf(Value key) {
        Node node = root;
        while (!node.isLeaf()) {
            InternalNode internal = (InternalNode) node;
            int idx = findChildIndex(internal.keys, key);
            node = internal.children.get(idx);
        }
        return (LeafNode) node;
    }

    private int findChildIndex(List<Value> keys, Value key) {
        int i = 0;
        while (i < keys.size() && compare(key, keys.get(i)) >= 0) {
            i++;
        }
        return i;
    }

    private int findKeyIndex(List<Value> keys, Value key) {
        int i = 0;
        while (i < keys.size() && compare(keys.get(i), key) < 0) {
            i++;
        }
        return i;
    }

    private InsertResult insertRecursive(Node node, Value key, Object value) {
        if (node.isLeaf()) {
            return insertIntoLeaf((LeafNode) node, key, value);
        } else {
            InternalNode internal = (InternalNode) node;
            int childIdx = findChildIndex(internal.keys, key);
            InsertResult result = insertRecursive(internal.children.get(childIdx), key, value);

            if (result.splitKey != null) {
                // Insert split key and new child into this internal node
                int insertIdx = findKeyIndex(internal.keys, result.splitKey);
                internal.keys.add(insertIdx, result.splitKey);
                internal.children.add(insertIdx + 1, result.newNode);

                if (internal.keys.size() >= order) {
                    return splitInternal(internal);
                }
            }
            return new InsertResult();
        }
    }

    private InsertResult insertIntoLeaf(LeafNode leaf, Value key, Object value) {
        int idx = findKeyIndex(leaf.keys, key);
        if (idx < leaf.keys.size() && compare(leaf.keys.get(idx), key) == 0) {
            // Key exists; add to value list (support duplicates)
            leaf.values.get(idx).add(value);
        } else {
            leaf.keys.add(idx, key);
            List<Object> valueList = new ArrayList<>();
            valueList.add(value);
            leaf.values.add(idx, valueList);
        }

        if (leaf.keys.size() >= order) {
            return splitLeaf(leaf);
        }
        return new InsertResult();
    }

    private InsertResult splitLeaf(LeafNode leaf) {
        LeafNode newLeaf = new LeafNode();
        int mid = leaf.keys.size() / 2;

        // Move half of the keys/values to new leaf
        newLeaf.keys.addAll(leaf.keys.subList(mid, leaf.keys.size()));
        newLeaf.values.addAll(leaf.values.subList(mid, leaf.values.size()));
        leaf.keys.subList(mid, leaf.keys.size()).clear();
        leaf.values.subList(mid, leaf.values.size()).clear();

        // Link leaves
        newLeaf.next = leaf.next;
        leaf.next = newLeaf;

        // Update firstLeaf if needed
        if (firstLeaf == leaf.next) firstLeaf = newLeaf;

        InsertResult result = new InsertResult();
        result.splitKey = newLeaf.keys.get(0);
        result.newNode = newLeaf;
        return result;
    }

    private InsertResult splitInternal(InternalNode node) {
        InternalNode newNode = new InternalNode();
        int mid = node.keys.size() / 2;

        Value splitKey = node.keys.get(mid);
        newNode.keys.addAll(node.keys.subList(mid + 1, node.keys.size()));
        newNode.children.addAll(node.children.subList(mid + 1, node.children.size()));
        node.keys.subList(mid, node.keys.size()).clear();
        node.children.subList(mid + 1, node.children.size()).clear();

        InsertResult result = new InsertResult();
        result.splitKey = splitKey;
        result.newNode = newNode;
        return result;
    }

    public List<Map.Entry<Value, Object>> getAllEntries() {
        List<Map.Entry<Value, Object>> result = new ArrayList<>();
        LeafNode leaf = firstLeaf;
        while (leaf != null) {
            for (int i = 0; i < leaf.keys.size(); i++) {
                for (Object val : leaf.values.get(i)) {
                    result.add(new AbstractMap.SimpleEntry<>(leaf.keys.get(i), val));
                }
            }
            leaf = leaf.next;
        }
        return result;
    }

    // --- Node classes ---
    private static class InsertResult {
        Value splitKey;
        Node newNode;
    }

    private abstract class Node {
        List<Value> keys = new ArrayList<>();
        abstract boolean isLeaf();
    }

    private class InternalNode extends Node {
        List<Node> children = new ArrayList<>();
        boolean isLeaf() { return false; }
    }

    private class LeafNode extends Node {
        List<List<Object>> values = new ArrayList<>();
        LeafNode next;
        boolean isLeaf() { return true; }
    }

    private class BPlusTreeIterator implements Iterator<Map.Entry<Value, Object>> {
        private LeafNode currentLeaf = firstLeaf;
        private int keyIdx = 0;
        private int valIdx = 0;

        @Override
        public boolean hasNext() {
            return currentLeaf != null && keyIdx < currentLeaf.keys.size();
        }

        @Override
        public Map.Entry<Value, Object> next() {
            if (!hasNext()) throw new NoSuchElementException();
            Value key = currentLeaf.keys.get(keyIdx);
            Object value = currentLeaf.values.get(keyIdx).get(valIdx);
            valIdx++;
            if (valIdx >= currentLeaf.values.get(keyIdx).size()) {
                valIdx = 0;
                keyIdx++;
                if (keyIdx >= currentLeaf.keys.size()) {
                    currentLeaf = currentLeaf.next;
                    keyIdx = 0;
                }
            }
            return new AbstractMap.SimpleEntry<>(key, value);
        }
    }
}

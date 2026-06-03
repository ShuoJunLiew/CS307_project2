package edu.sustech.cs307.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sustech.cs307.record.RID;
import edu.sustech.cs307.value.Value;
import org.pmw.tinylog.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * B+ Tree backed index implementation with JSON persistence.
 * Implements the Index interface for use by the query engine.
 */
public class BPlusTreeIndex implements Index {

    private static final int DEFAULT_ORDER = 4;
    private final BPlusTree tree;
    private final String persistPath;

    public BPlusTreeIndex(String persistPath) {
        this.persistPath = persistPath;
        BPlusTree loaded = loadFromDisk(persistPath);
        this.tree = loaded != null ? loaded : new BPlusTree(DEFAULT_ORDER);
    }

    public void insert(Value key, RID value) {
        tree.insert(key, value);
    }

    public int size() {
        return tree.size();
    }

    public boolean isEmpty() {
        return tree.isEmpty();
    }

    public void persist() {
        try {
            File file = new File(persistPath);
            file.getParentFile().mkdirs();
            ObjectMapper mapper = new ObjectMapper();
            // Use TreeMap format for compatibility with InMemoryOrderedIndex
            TreeMap<Value, RID> map = new TreeMap<>((v1, v2) -> {
                try {
                    return edu.sustech.cs307.value.ValueComparer.compare(v1, v2);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            List<Map.Entry<Value, Object>> entries = tree.getAllEntries();
            for (Map.Entry<Value, Object> e : entries) {
                map.put(e.getKey(), (RID) e.getValue());
            }
            mapper.writeValue(file, map);
        } catch (IOException e) {
            Logger.error("Error persisting index: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private BPlusTree loadFromDisk(String path) {
        try {
            File file = new File(path);
            if (file.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                TypeReference<Map<String, RID>> typeRef = new TypeReference<>() {};
                Map<String, RID> loaded = mapper.readValue(file, typeRef);
                if (loaded != null && !loaded.isEmpty()) {
                    BPlusTree loadedTree = new BPlusTree(DEFAULT_ORDER);
                    for (Map.Entry<String, RID> entry : loaded.entrySet()) {
                        Value key = parseKey(entry.getKey());
                        if (key != null) {
                            loadedTree.insert(key, entry.getValue());
                        }
                    }
                    return loadedTree;
                }
            }
        } catch (IOException e) {
            Logger.error("Error loading index data: " + e.getMessage());
        }
        return null;
    }

    private Value parseKey(String keyStr) {
        if (keyStr == null) return null;
        try {
            long longVal = Long.parseLong(keyStr);
            return new Value(longVal);
        } catch (NumberFormatException ignored) {}
        try {
            double doubleVal = Double.parseDouble(keyStr);
            return new Value(doubleVal);
        } catch (NumberFormatException ignored) {}
        return new Value(keyStr);
    }

    @Override
    public RID EqualTo(Value value) {
        Object result = tree.search(value);
        return result != null ? (RID) result : null;
    }

    @Override
    public Iterator<Map.Entry<Value, RID>> LessThan(Value value, boolean isEqual) {
        List<Map.Entry<Value, RID>> results = new ArrayList<>();
        List<Map.Entry<Value, Object>> rangeResults = tree.rangeSearch(null, true, value, isEqual);
        for (Map.Entry<Value, Object> e : rangeResults) {
            results.add(new AbstractMap.SimpleEntry<>(e.getKey(), (RID) e.getValue()));
        }
        Collections.reverse(results);
        return results.iterator();
    }

    @Override
    public Iterator<Map.Entry<Value, RID>> MoreThan(Value value, boolean isEqual) {
        List<Map.Entry<Value, RID>> results = new ArrayList<>();
        List<Map.Entry<Value, Object>> rangeResults = tree.rangeSearch(value, isEqual, null, true);
        for (Map.Entry<Value, Object> e : rangeResults) {
            results.add(new AbstractMap.SimpleEntry<>(e.getKey(), (RID) e.getValue()));
        }
        return results.iterator();
    }

    @Override
    public Iterator<Map.Entry<Value, RID>> Range(Value low, Value high, boolean leftEqual, boolean rightEqual) {
        List<Map.Entry<Value, RID>> results = new ArrayList<>();
        List<Map.Entry<Value, Object>> rangeResults = tree.rangeSearch(low, leftEqual, high, rightEqual);
        for (Map.Entry<Value, Object> e : rangeResults) {
            results.add(new AbstractMap.SimpleEntry<>(e.getKey(), (RID) e.getValue()));
        }
        return results.iterator();
    }

    /**
     * Returns an iterator over all index entries, sorted by key.
     */
    public Iterator<Map.Entry<Value, RID>> scanAll() {
        List<Map.Entry<Value, RID>> results = new ArrayList<>();
        List<Map.Entry<Value, Object>> allEntries = tree.getAllEntries();
        for (Map.Entry<Value, Object> e : allEntries) {
            results.add(new AbstractMap.SimpleEntry<>(e.getKey(), (RID) e.getValue()));
        }
        return results.iterator();
    }
}

package edu.sustech.cs307.system;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.exception.ExceptionTypes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class TransactionManager {

    private final DBManager dbManager;
    private boolean inTransaction;
    private Path transactionSnapshot;
    private final Map<String, Deque<Path>> savepointStacks;

    public TransactionManager(DBManager dbManager) {
        this.dbManager = dbManager;
        this.inTransaction = false;
        this.savepointStacks = new HashMap<>();
    }

    public void begin() throws DBException {
        if (inTransaction) {
            throw new DBException(ExceptionTypes.TransactionAlreadyActive());
        }
        transactionSnapshot = createSnapshot();
        inTransaction = true;
    }

    public void commit() throws DBException {
        if (!inTransaction) return;
        dbManager.persistRuntimeState();
        cleanupAllSnapshots();
        inTransaction = false;
        transactionSnapshot = null;
    }

    public void rollback() throws DBException {
        if (!inTransaction) return;
        try {
            restoreFromSnapshot(transactionSnapshot);
        } finally {
            cleanupAllSnapshots();
            inTransaction = false;
            transactionSnapshot = null;
        }
    }

    public void savepoint(String savepointName) throws DBException {
        if (!inTransaction) {
            throw new DBException(ExceptionTypes.TransactionRequired());
        }
        Path snapshot = createSnapshot();
        savepointStacks.computeIfAbsent(savepointName, k -> new ArrayDeque<>()).push(snapshot);
    }

    public void rollbackToSavepoint(String savepointName) throws DBException {
        if (!inTransaction) {
            throw new DBException(ExceptionTypes.TransactionRequired());
        }
        Deque<Path> stack = savepointStacks.get(savepointName);
        if (stack == null || stack.isEmpty()) {
            throw new DBException(ExceptionTypes.SavepointDoesNotExist(savepointName));
        }
        Path target = stack.peek();
        // Clean up savepoints created AFTER this one (for all names)
        // A rollback affects the current state; nested savepoints need not be cleaned
        restoreFromSnapshot(target);
    }

    public void releaseSavepoint(String savepointName) throws DBException {
        if (!inTransaction) {
            throw new DBException(ExceptionTypes.TransactionRequired());
        }
        Deque<Path> stack = savepointStacks.get(savepointName);
        if (stack == null || stack.isEmpty()) {
            throw new DBException(ExceptionTypes.SavepointDoesNotExist(savepointName));
        }
        Path snapshot = stack.pop();
        deleteDirectoryQuietly(snapshot);
        if (stack.isEmpty()) {
            savepointStacks.remove(savepointName);
        }
    }

    // --- Internal ---

    private Path createSnapshot() throws DBException {
        dbManager.persistRuntimeState();
        Path snapshotDir;
        try {
            snapshotDir = Files.createTempDirectory("cs307-txn-");
            copyDirectoryContents(getDbRoot(), snapshotDir);
        } catch (IOException e) {
            throw new DBException(ExceptionTypes.BadIOError(e.getMessage()));
        }
        return snapshotDir;
    }

    private void restoreFromSnapshot(Path snapshot) throws DBException {
        // Flush and clear current buffer pool state
        dbManager.getBufferPool().FlushAllPages("");
        dbManager.getBufferPool().ClearAllPages();
        // Delete current db contents and restore from snapshot
        try {
            deleteDirectoryContents(getDbRoot());
            copyDirectoryContents(snapshot, getDbRoot());
        } catch (IOException e) {
            throw new DBException(ExceptionTypes.BadIOError(e.getMessage()));
        }
        // Reload in-memory metadata from restored files
        dbManager.getMetaManager().reload();
    }

    private void cleanupAllSnapshots() {
        deleteDirectoryQuietly(transactionSnapshot);
        transactionSnapshot = null;
        for (Deque<Path> stack : savepointStacks.values()) {
            while (!stack.isEmpty()) {
                deleteDirectoryQuietly(stack.pop());
            }
        }
        savepointStacks.clear();
    }

    private Path getDbRoot() {
        return Path.of(dbManager.getDiskManager().getCurrentDir());
    }

    private void copyDirectoryContents(Path sourceRoot, Path targetRoot) throws IOException {
        if (!Files.exists(sourceRoot)) {
            Files.createDirectories(targetRoot);
            return;
        }
        Files.createDirectories(targetRoot);
        try (var paths = Files.walk(sourceRoot)) {
            for (Path source : paths.toList()) {
                Path relative = sourceRoot.relativize(source);
                Path target = targetRoot.resolve(relative);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private void deleteDirectoryContents(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {}
            });
        }
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (dir == null) return;
        try {
            deleteDirectoryContents(dir);
        } catch (IOException ignored) {}
    }
}

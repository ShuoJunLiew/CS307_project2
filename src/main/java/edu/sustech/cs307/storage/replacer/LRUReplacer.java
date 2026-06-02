package edu.sustech.cs307.storage.replacer;

import java.util.*;

public class LRUReplacer implements PageReplacer {

    private final int maxSize;
    private final Set<Integer> pinnedFrames = new HashSet<>();
    private final Set<Integer> LRUHash = new HashSet<>();
    private final LinkedList<Integer> LRUList = new LinkedList<>();

    public LRUReplacer(int numPages) {
        this.maxSize = numPages;
    }

    @Override
    public int Victim() {
        if (LRUList.isEmpty()) {
            return -1;
        }
        // Evict the least recently used frame from the tail
        int victimFrameId = LRUList.removeLast();
        LRUHash.remove(victimFrameId);
        return victimFrameId;
    }

    @Override
    public void Pin(int frameId) {
        // Case 1: Already pinned, do nothing
        if (pinnedFrames.contains(frameId)) {
            return;
        }

        // Case 2: In the evictable list, move it to pinned
        if (LRUHash.contains(frameId)) {
            LRUList.removeFirstOccurrence(frameId);
            LRUHash.remove(frameId);
            pinnedFrames.add(frameId);
            return;
        }

        // Case 3: Completely new frame, check total storage capacity
        if (size() >= maxSize) {
            throw new RuntimeException("REPLACER IS FULL");
        }
        pinnedFrames.add(frameId);
    }

    @Override
    public void Unpin(int frameId) {
        // If it's already unpinned/evictable, your test expects it to throw an exception or ignore
        if (LRUHash.contains(frameId)) {
            throw new RuntimeException("UNPIN PAGE NOT FOUND");
        }

        // Must be in pinned set to be eligible for unpinning
        if (!pinnedFrames.contains(frameId)) {
            throw new RuntimeException("UNPIN PAGE NOT FOUND");
        }

        pinnedFrames.remove(frameId);
        // Add to head as most recently used evictable page
        LRUList.addFirst(frameId);
        LRUHash.add(frameId);
    }

    @Override
    public int size() {
        // Returns the total number of frames currently tracked by the replacer
        return LRUList.size() + pinnedFrames.size();
    }
}
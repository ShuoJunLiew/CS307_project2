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

    public int Victim() {
        // If LRUList is empty, no evictable frames
        if (LRUList.isEmpty()) {
            return -1;
        }
        // The least recently used frame is at the tail (end) of the list
        int victimFrameId = LRUList.removeLast();
        LRUHash.remove(victimFrameId);
        return victimFrameId;
    }

    public void Pin(int frameId) {
        // Check if we've reached capacity
        if (size() >= maxSize && !pinnedFrames.contains(frameId) && !LRUHash.contains(frameId)) {
            throw new RuntimeException("REPLACER IS FULL");
        }

        // Case 1: Frame is currently in LRUList (evictable)
        if (LRUHash.contains(frameId)) {
            // Remove it from LRUList and LRUHash
            LRUList.removeFirstOccurrence(frameId);
            LRUHash.remove(frameId);
        }
        // Case 2: Frame is NOT in LRUHash (either already pinned or new)
        // In either case, add to pinnedFrames
        pinnedFrames.add(frameId);
    }


    public void Unpin(int frameId) {
        // If frame is already in LRUHash, do nothing (already evictable)
        if (LRUHash.contains(frameId)) {
            return;
        }

        // If frame is in pinnedFrames, remove it from pinnedFrames
        if (pinnedFrames.contains(frameId)) {
            pinnedFrames.remove(frameId);
            // Add it back to LRU structure (as most recently used)
            LRUList.addFirst(frameId);  // Add to head = most recent
            LRUHash.add(frameId);
            return;
        }

        // Frame not found in either data structure
        throw new RuntimeException("UNPIN PAGE NOT FOUND");
    }


    public int size() {
        return LRUList.size() + pinnedFrames.size();
    }
}
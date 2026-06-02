package edu.sustech.cs307.storage.replacer;

import java.util.HashSet;
import java.util.Set;

public class ClockReplacer implements PageReplacer {

    private final int capacity;
    private final int[] frames;        // frameId at each clock position (-1 if empty)
    private final boolean[] refBits;   // reference bit for each position
    private int clockHand;             // current hand position
    private int evictableSize;         // count of unpinned frames
    private final Set<Integer> pinnedFrames = new HashSet<>();

    public ClockReplacer(int numPages) {
        this.capacity = numPages;
        this.frames = new int[numPages];
        this.refBits = new boolean[numPages];
        this.clockHand = 0;
        this.evictableSize = 0;

        for (int i = 0; i < numPages; i++) {
            frames[i] = -1;
            refBits[i] = false;
        }
    }

    @Override
    public int Victim() {
        if (evictableSize == 0) {
            return -1;
        }

        while (true) {
            int currentPos = clockHand;
            int currentFrame = frames[currentPos];

            // If the slot contains an evictable frame
            if (currentFrame != -1) {
                if (refBits[currentPos]) {
                    // Give a second chance
                    refBits[currentPos] = false;
                } else {
                    // Victim found! Clean slot and evict
                    int victimFrame = currentFrame;
                    frames[currentPos] = -1;
                    evictableSize--;
                    clockHand = (clockHand + 1) % capacity;
                    return victimFrame;
                }
            }
            clockHand = (clockHand + 1) % capacity;
        }
    }

    @Override
    public void Pin(int frameId) {
        if (pinnedFrames.contains(frameId)) {
            return;
        }

        int position = findFramePosition(frameId);
        if (position != -1) {
            // Remove from the evictable clock array and move to pinned
            frames[position] = -1;
            refBits[position] = false;
            evictableSize--;
            pinnedFrames.add(frameId);
        } else {
            // New frame: check full capacity boundaries
            if (size() >= capacity) {
                throw new RuntimeException("REPLACER IS FULL");
            }
            pinnedFrames.add(frameId);
        }
    }

    @Override
    public void Unpin(int frameId) {
        if (findFramePosition(frameId) != -1) {
            throw new RuntimeException("UNPIN PAGE NOT FOUND");
        }

        if (!pinnedFrames.contains(frameId)) {
            throw new RuntimeException("UNPIN PAGE NOT FOUND");
        }

        pinnedFrames.remove(frameId);
        int emptyPos = findEmptySlot();
        if (emptyPos == -1) {
            throw new RuntimeException("No empty slot in clock");
        }

        frames[emptyPos] = frameId;
        refBits[emptyPos] = true;
        evictableSize++;
    }

    private int findFramePosition(int frameId) {
        for (int i = 0; i < capacity; i++) {
            if (frames[i] == frameId) {
                return i;
            }
        }
        return -1;
    }

    private int findEmptySlot() {
        for (int i = 0; i < capacity; i++) {
            if (frames[i] == -1) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int size() {
        return evictableSize + pinnedFrames.size();
    }
}
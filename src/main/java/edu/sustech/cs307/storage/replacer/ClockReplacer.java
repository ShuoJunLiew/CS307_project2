package edu.sustech.cs307.storage.replacer;

import java.util.HashSet;
import java.util.Set;

public class ClockReplacer implements PageReplacer {

    private final int capacity;
    private final int[] frames;        // frameId at each clock position (-1 if empty)
    private final boolean[] refBits;   // reference bit for each position
    private int clockHand;             // current hand position
    private int size;                  // number of frames in clock (evictable)
    private final Set<Integer> pinnedFrames;  // pinned frames (cannot be evicted)

    public ClockReplacer(int numPages) {
        this.capacity = numPages;
        this.frames = new int[numPages];
        this.refBits = new boolean[numPages];
        this.clockHand = 0;
        this.size = 0;
        this.pinnedFrames = new HashSet<>();

        // Initialize all frame slots as empty
        for (int i = 0; i < numPages; i++) {
            frames[i] = -1;
            refBits[i] = false;
        }
    }

    /**
     * Find a victim frame to evict using the Clock (second-chance) algorithm.
     * @return frameId of victim, or -1 if no evictable frame exists
     */
    @Override
    public int Victim() {
        if (size == 0) {
            return -1;
        }

        // Keep scanning until we find a victim
        while (true) {
            int currentPos = clockHand;
            int currentFrame = frames[currentPos];

            // Skip empty slots (shouldn't happen if size tracking is correct)
            if (currentFrame == -1) {
                clockHand = (clockHand + 1) % capacity;
                continue;
            }

            // Check reference bit
            if (refBits[currentPos]) {
                // Second chance: clear ref bit and move on
                refBits[currentPos] = false;
                clockHand = (clockHand + 1) % capacity;
            } else {
                // Victim found!
                int victimFrame = currentFrame;
                // Remove from clock
                frames[currentPos] = -1;
                refBits[currentPos] = false;
                size--;
                // Move hand to next position
                clockHand = (clockHand + 1) % capacity;
                return victimFrame;
            }
        }
    }

    /**
     * Pin a frame - mark as in use, remove from clock if present.
     * @param frameId the frame to pin
     * @throws RuntimeException if replacer is full and frame is new
     */
    @Override
    public void Pin(int frameId) {
        // Check if frame is already pinned (do nothing)
        if (pinnedFrames.contains(frameId)) {
            return;
        }

        // Check if frame is in clock (evictable)
        int position = findFramePosition(frameId);
        if (position != -1) {
            // Remove from clock
            frames[position] = -1;
            refBits[position] = false;
            size--;
        } else {
            // New frame - check capacity
            if (pinnedFrames.size() + size >= capacity) {
                throw new RuntimeException("REPLACER IS FULL");
            }
        }

        // Add to pinned frames
        pinnedFrames.add(frameId);
    }

    /**
     * Unpin a frame - mark as eligible for eviction, add to clock.
     * @param frameId the frame to unpin
     * @throws RuntimeException if frame is not found (neither pinned nor in clock)
     */
    @Override
    public void Unpin(int frameId) {
        // Check if frame is pinned
        if (pinnedFrames.contains(frameId)) {
            pinnedFrames.remove(frameId);

            // Find an empty slot in clock
            int emptyPos = findEmptySlot();
            if (emptyPos == -1) {
                // This shouldn't happen if capacity logic is correct
                throw new RuntimeException("No empty slot in clock");
            }

            // Add to clock with refBit = 1 (recently used)
            frames[emptyPos] = frameId;
            refBits[emptyPos] = true;
            size++;
            return;
        }

        // If frame is already in clock (already unpinned), this is an error
        // Because you can't unpin a frame that isn't pinned
        if (findFramePosition(frameId) != -1) {
            throw new RuntimeException("UNPIN PAGE NOT FOUND");
        }

        // Frame not found anywhere
        throw new RuntimeException("UNPIN PAGE NOT FOUND");
    }

    /**
     * Find the position of a frameId in the clock array.
     * @param frameId the frame to search for
     * @return position index, or -1 if not found
     */
    private int findFramePosition(int frameId) {
        for (int i = 0; i < capacity; i++) {
            if (frames[i] == frameId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find an empty slot in the clock array.
     * @return position index, or -1 if no empty slot
     */
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
        return size + pinnedFrames.size();
    }
}
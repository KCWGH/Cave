package com.blackberry.cave;

import java.io.*;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreNotFoundException;

// Only versioned byte arrays are persisted; bitmaps, screens and timers never are.
final class SaveStore {
    private static final int MAGIC = 0x43415645;
    private static final int VERSION = 2;
    private static final int MAX_BYTES = 65536;

    private static String storeName(int slot) {
        if (slot < 0 || slot >= 3) throw new IllegalArgumentException("Invalid slot");
        return "CaveSave" + (slot + 1);
    }

    private static void closeStore(RecordStore store) {
        if (store == null) return;
        try { store.closeRecordStore(); }
        catch (RecordStoreException ignored) {
            // Record writes are synchronous; a cleanup failure does not undo a saved record.
        }
    }

    static synchronized boolean occupied(int slot) throws IOException {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(storeName(slot), false);
            return store.getNumRecords() != 0;
        } catch (RecordStoreNotFoundException empty) {
            return false;
        } catch (RecordStoreException failure) {
            throw new IOException(failure.toString());
        } finally { closeStore(store); }
    }

    private static int checksum(byte[] bytes, int count) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < count; i++) hash = (hash ^ (bytes[i] & 255)) * 16777619;
        return hash;
    }

    static synchronized void save(int slot, HexGameScreen game) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC); out.writeInt(VERSION); out.writeLong(System.currentTimeMillis());
        game.writeSave(out);
        out.flush();
        byte[] payload = bytes.toByteArray();
        out.writeInt(checksum(payload, payload.length)); out.flush();
        byte[] complete = bytes.toByteArray();
        if (complete.length > MAX_BYTES) throw new IOException("Save too large");
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(storeName(slot), true);
            int count = store.getNumRecords();
            // One complete save per store. RMS add/set operations are atomic.
            if (count == 0) store.addRecord(complete, 0, complete.length);
            else if (count == 1) store.setRecord(1, complete, 0, complete.length);
            else throw new IOException("Invalid save store");
        } catch (RecordStoreException failure) {
            throw new IOException(failure.toString());
        } finally { closeStore(store); }
    }

    static synchronized HexGameScreen load(int slot) throws IOException {
        RecordStore store = null;
        byte[] bytes;
        try {
            store = RecordStore.openRecordStore(storeName(slot), false);
            if (store.getNumRecords() != 1) throw new IOException("Empty or invalid save");
            int size = store.getRecordSize(1);
            if (size < 20 || size > MAX_BYTES) throw new IOException("Invalid save size");
            bytes = store.getRecord(1);
        } catch (RecordStoreException failure) {
            throw new IOException(failure.toString());
        } finally { closeStore(store); }
        if (bytes.length < 20 || bytes.length > MAX_BYTES) throw new IOException("Invalid save size");
        int n = bytes.length;
        int stored = ((bytes[n-4] & 255) << 24) | ((bytes[n-3] & 255) << 16)
                | ((bytes[n-2] & 255) << 8) | (bytes[n-1] & 255);
        if (stored != checksum(bytes, n - 4)) throw new IOException("Damaged save");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, n - 4));
        if (in.readInt() != MAGIC)
            throw new IOException("Unsupported save version");
        int version = in.readInt();
        if (version < 1 || version > VERSION) throw new IOException("Unsupported save version");
        in.readLong();
        HexGameScreen game = new HexGameScreen(in, slot, version);
        if (in.available() != 0) throw new IOException("Unexpected save data");
        return game;
    }

    static String label(int slot) throws IOException {
        return "Slot " + (slot + 1) + (occupied(slot) ? " - Saved" : " - Empty");
    }
}

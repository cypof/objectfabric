/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.com).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.objectfabric.misc;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Properties;

import com.objectfabric.Privileged;
import com.objectfabric.Stats;
import com.objectfabric.Strings;

/**
 * Platform classes allow other implementations to be used for ports like GWT and .NET.
 * The .NET specific implementations make it possible to remove Java components like
 * Reflection and Security from the ObjectFabric dll.
 */
public final class PlatformFile extends Privileged {

    // TODO inject exceptions for testing

    private final String _name;

    private final RandomAccessFile _file;

    public PlatformFile(String name) throws IOException {
        _name = name;
        _file = new RandomAccessFile(name, "rw");
    }

    public long length() {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": length()");

        try {
            return _file.length();
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    //

    public void readOrZero(byte[] buffer) {
        readOrZero(buffer, 0, buffer.length);
    }

    public void readOrZero(byte[] buffer, int offset, int length) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": readOrZero(length: " + length + ")");

        try {
            int remaining = length;
            int pos = offset;

            while (remaining > 0) {
                int read = _file.read(buffer, pos, remaining);

                if (read == -1) {
                    for (int i = pos; i < buffer.length; i++)
                        buffer[i] = 0;

                    break;
                }

                remaining -= read;
                pos += read;

                if (Stats.ENABLED) {
                    Stats.getInstance().FileReadCount.incrementAndGet();
                    Stats.getInstance().FileTotalRead.addAndGet(read);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    /**
     * @throws RuntimeIOException
     *             if less data than requested
     */
    public void readFull(byte[] buffer) {
        readFull(buffer, 0, buffer.length);
    }

    /**
     * @throws RuntimeIOException
     *             if less data than requested
     */
    public void readFull(byte[] buffer, int offset, int length) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": readFull(length: " + length + ")");

        try {
            int remaining = length;
            int pos = offset;

            while (remaining > 0) {
                int read = _file.read(buffer, pos, remaining);

                if (read == -1)
                    throw new EOFException();

                remaining -= read;
                pos += read;

                if (Stats.ENABLED) {
                    Stats.getInstance().FileReadCount.incrementAndGet();
                    Stats.getInstance().FileTotalRead.addAndGet(read);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    public short readShort() {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": readShort()");

        if (Stats.ENABLED) {
            Stats.getInstance().FileReadCount.incrementAndGet();
            Stats.getInstance().FileTotalRead.addAndGet(2);
        }

        try {
            return _file.readShort();
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    public int readInt() {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": readInt()");

        if (Stats.ENABLED) {
            Stats.getInstance().FileReadCount.incrementAndGet();
            Stats.getInstance().FileTotalRead.addAndGet(4);
        }

        try {
            return _file.readInt();
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    public long readLong() {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": readLong()");

        if (Stats.ENABLED) {
            Stats.getInstance().FileReadCount.incrementAndGet();
            Stats.getInstance().FileTotalRead.addAndGet(8);
        }

        try {
            return _file.readLong();
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }

    }

    //

    public void write(byte[] buffer) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": write(buffer: " + buffer.length + ")");

        if (Stats.ENABLED) {
            Stats.getInstance().FileWriteCount.incrementAndGet();
            Stats.getInstance().FileTotalWritten.addAndGet(buffer.length);
        }

        try {
            _file.write(buffer);
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    public void write(byte[] buffer, int offset, int length) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": write(length: " + length + ")");

        if (Stats.ENABLED) {
            Stats.getInstance().FileWriteCount.incrementAndGet();
            Stats.getInstance().FileTotalWritten.addAndGet(length);
        }

        try {
            _file.write(buffer, offset, length);
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    public void writeShort(short value) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": writeShort()");

        if (Stats.ENABLED) {
            Stats.getInstance().FileWriteCount.incrementAndGet();
            Stats.getInstance().FileTotalWritten.addAndGet(2);
        }

        try {
            _file.writeShort(value);
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    public void writeInt(int value) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": writeInt()");

        if (Stats.ENABLED) {
            Stats.getInstance().FileWriteCount.incrementAndGet();
            Stats.getInstance().FileTotalWritten.addAndGet(4);
        }

        try {
            _file.writeInt(value);
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    public void writeLong(long value) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": writeLong()");

        if (Stats.ENABLED) {
            Stats.getInstance().FileWriteCount.incrementAndGet();
            Stats.getInstance().FileTotalWritten.addAndGet(8);
        }

        try {
            _file.writeLong(value);
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    //

    public long remaining() {
        return length() - getOffset();
    }

    public long getOffset() {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": getOffset()");

        try {
            return _file.getFilePointer();
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    public void setOffset(long offset) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": setOffset()");

        try {
            _file.seek(offset);
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    public void setLength(long value) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": setLength(value: " + value + ")");

        try {
            _file.setLength(value);
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    public void sync() {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": sync()");

        try {
            _file.getFD().sync();
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    public void close() {
        if (Debug.PERSISTENCE_LOG)
            Log.write("PlatformFile " + _name + ": close()");

        try {
            _file.close();
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    //

    public static void write(String path, char[] text, int length) {
        File file = new File(path);
        FileWriter writer;

        try {
            writer = new FileWriter(file);
            writer.write(text, 0, length);
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }

        try {
            writer.close();
        } catch (IOException ex) {
            // Ignore
        }
    }

    public static void deleteFile(String path) {
        if (!new File(path).delete())
            throw new RuntimeIOException(Strings.DELETE_FILE_FAILED + path);
    }

    public static void deleteFileIfExists(String path) {
        File file = new File(path);

        if (file.exists() && !file.delete())
            throw new RuntimeIOException(Strings.DELETE_FILE_FAILED + path);
    }

    public static void deleteFiles(String folder) {
        deleteFiles(folder, null);
    }

    public static void deleteFiles(String folder, String extension) {
        deleteFiles(new File(folder), extension);
    }

    private static void deleteFiles(File folder, String extension) {
        if (folder.exists()) {
            for (File child : folder.listFiles()) {
                if (child.isDirectory())
                    deleteFiles(child, extension);
                else if (extension == null || child.getName().endsWith(extension))
                    if (!child.delete())
                        throw new RuntimeIOException(Strings.DELETE_FILE_FAILED + child);
            }
        }
    }

    public static boolean exists(String file) {
        return new File(file).exists();
    }

    public static void mkdir(String folder) {
        new File(folder).mkdir();
    }

    public static String readCopyright() {
        Properties jautodoc = new Properties();

        try {
            jautodoc.load(new FileInputStream("../objectfabric/.settings/net.sf.jautodoc.prefs"));
        } catch (Exception ex) {
            throw new RuntimeIOException(ex);
        }

        return jautodoc.getProperty("header_text");
    }
}

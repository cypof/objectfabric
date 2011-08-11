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

package com.objectfabric.generator;

import java.io.File;
import java.util.EnumSet;

import com.objectfabric.CompileTimeSettings;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformFile;
import com.objectfabric.misc.PlatformSet;
import com.objectfabric.misc.SparseArrayHelper;
import com.objectfabric.misc.Utils;

public class Generator {

    public enum Flag {
        /**
         * Deletes all files in target folder before generation.
         */
        DELETE_TARGET_FOLDER_FILES,

        /**
         * Do not generate synchronous methods on generated classes, this is handy if you
         * want to make sure no thread will ever block in your application for best
         * performance, or in environments like GWT where blocking a thread is not
         * allowed.
         */
        GENERATE_ONLY_ASYNCHRONOUS_METHODS,

        /**
         * Do not modify caps of the first letter of the name of method to accommodate C#
         * and Java conventions.
         */
        NO_METHOD_NAME_CAPS_CHANGE
    }

    public static final String SCHEMA_FILE = "objectfabric-" + CompileTimeSettings.OBJECTFABRIC_VERSION + ".xsd";

    private ObjectModelDef _model;

    private byte[] _uid;

    private String _xml;

    private String _folder;

    private Target _target;

    private EnumSet<Flag> _flags;

    private String _copyright;

    private char[] _cache = new char[1024];

    private int _cacheLength;

    public Generator() {
        this(null);
    }

    public Generator(ObjectModelDef model) {
        _model = model;
    }

    public ObjectModelDef getObjectModel() {
        return _model;
    }

    public void setObjectModel(ObjectModelDef value) {
        _model = value;
    }

    public byte[] getLastObjectModelUID() {
        return _uid;
    }

    public String getXMLPath() {
        return _xml;
    }

    public void setXMLPath(String value) {
        _xml = value;
    }

    public String getFolder() {
        return _folder;
    }

    public void setFolder(String value) {
        _folder = value;
    }

    public Target getTarget() {
        return _target;
    }

    public void setTarget(Target value) {
        _target = value;
    }

    public EnumSet<Flag> getFlags() {
        return _flags;
    }

    public void setFlags(EnumSet<Flag> value) {
        _flags = value;
    }

    public String getCopyright() {
        return _copyright;
    }

    public void setCopyright(String value) {
        _copyright = value;
    }

    boolean isJava() {
        return _target == Target.JAVA | getTarget() == Target.GWT;
    }

    boolean isCSharp() {
        return _target == Target.CSHARP;
    }

    protected void append(String text) {
        ensureCapacity(_cacheLength + text.length());
        text.getChars(0, text.length(), _cache, _cacheLength);
        _cacheLength += text.length();
    }

    protected void append(StringBuilder text) {
        ensureCapacity(_cacheLength + text.length());
        text.getChars(0, text.length(), _cache, _cacheLength);
        _cacheLength += text.length();
    }

    private void ensureCapacity(int newCapacity) {
        int capacity = _cache.length;

        while (capacity < newCapacity)
            capacity <<= SparseArrayHelper.TIMES_TWO_SHIFT;

        if (capacity != _cache.length) {
            char[] temp = new char[capacity];
            PlatformAdapter.arraycopy(_cache, 0, temp, 0, _cacheLength);
            _cache = temp;
        }
    }

    protected void write(FileGenerator file) {
        file.generate();
        writeCacheToFile(file);
    }

    protected void replace(String a, String b) {
        String s = new String(_cache, 0, _cacheLength);
        s = s.replace(a, b);
        ensureCapacity(s.length());
        s.getChars(0, s.length(), _cache, 0);
        _cacheLength = s.length();
    }

    protected void writeCacheToFile(FileGenerator file) {
        _target.onWritingFile(this, file);
        PlatformFile.write(file.Path, _cache, _cacheLength);
        _cacheLength = 0;
        System.out.println("Generated " + file.Path);
    }

    public void run(String folder) {
        run(folder, (Target) null);
    }

    public void run(String folder, Target target) {
        run(folder, target, null, null);
    }

    /**
     * ObjectModels are identified using a UID. This method can be used to generate object
     * models that will be considered identical by ObjectFabric. E.g. to replicate a model
     * between Java, GWT or .NET processes. Make sure that all models generated using the
     * same UID are strictly identical, or replication will fail possibly without
     * descriptive error message.
     */
    public void run(String folder, byte[] uid) {
        run(folder, null, uid, null);
    }

    public void run(String folder, EnumSet<Flag> flags) {
        run(folder, null, null, flags);
    }

    public void run(String folder, Target target, byte[] uid) {
        run(folder, target, uid, null, null);
    }

    public void run(String folder, Target target, byte[] uid, EnumSet<Flag> flags) {
        run(folder, target, uid, flags, null);
    }

    List<FileGenerator> run(String folder, Target target, byte[] uid, EnumSet<Flag> flags, Visitor visitor) {
        if (target == null)
            target = PlatformAdapter.PLATFORM == CompileTimeSettings.PLATFORM_JAVA ? Target.JAVA : Target.CSHARP;

        return target.writeFiles(this, folder, uid, flags, visitor);
    }

    List<FileGenerator> run(byte[] uid, Visitor visitor) {
        if (uid == null)
            uid = PlatformAdapter.createUID();

        if (uid.length != PlatformAdapter.UID_BYTES_COUNT)
            throw new IllegalArgumentException();

        _uid = uid;
        _model.prepare();

        if (visitor != null)
            visitor.visit(_model);

        PlatformSet<String> dirs = new PlatformSet<String>();
        List<FileGenerator> files = new List<FileGenerator>();

        for (int i = 0; i < _model.getAllPackages().size(); i++) {
            PackageDef p = _model.getAllPackages().get(i);
            String path = getFolder();

            if (isJava())
                path += "/" + p.getFullName().replace('.', '/');

            if (!dirs.contains(path)) {
                File directory = new File(path);
                directory.mkdirs();

                if (getFlags().contains(Flag.DELETE_TARGET_FOLDER_FILES))
                    PlatformFile.deleteFiles(directory.getAbsolutePath(), getTarget().extension());

                dirs.add(path);
            }

            for (int j = 0; j < p.Classes.size(); j++) {
                FileGenerator file = new FileGeneratorClass(this, p.Classes.get(j));
                write(file);
                files.add(file);
            }
        }

        FileGenerator file = new FileGeneratorObjectModel(this);
        write(file);
        files.add(file);

        return files;
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        Generator generator = new Generator();
        int result = generator.parseArgs(args);

        if (result == 0) {
            ObjectModelDef model = ObjectModelDef.fromXMLFile(generator.getXMLPath());
            generator.setObjectModel(model);
            generator.run(PlatformAdapter.createUID(), null);
        }

        return result;
    }

    int parseArgs(String[] args) {
        final String XML = "-xml:";
        final String OUT = "-out:";
        final String TARGET = "-target:";
        final String JAVA = "java";
        final String CS = "cs";
        final String GWT = "gwt";
        final String NO_SYNC = "-nosync";

        for (String arg : args) {
            if (arg.startsWith(XML)) {
                setXMLPath(arg.substring(XML.length()));

                if (!PlatformFile.exists(getXMLPath())) {
                    System.out.println("Input file does not exist: " + getXMLPath());
                    return 1;
                }
            } else if (arg.startsWith(OUT)) {
                setFolder(arg.substring(OUT.length()));
                PlatformFile.mkdir(getFolder());
            } else if (arg.startsWith(TARGET)) {
                String t = arg.substring(TARGET.length());

                if (JAVA.equals(t))
                    setTarget(Target.JAVA);
                else if (GWT.equals(t))
                    setTarget(Target.GWT);
                else if (CS.equals(t))
                    setTarget(Target.CSHARP);
                else {
                    System.out.println("Invalid target: " + arg);
                    return 1;
                }
            } else if (arg.equals(NO_SYNC))
                setFlags(EnumSet.of(Flag.GENERATE_ONLY_ASYNCHRONOUS_METHODS));
            else {
                System.out.println("Invalid argument: " + arg);
                return 1;
            }
        }

        if (getXMLPath() == null || getFolder() == null) {
            String version = CompileTimeSettings.OBJECTFABRIC_VERSION;
            int pad = 12;

            System.out.println("");// ////////////////////////////////////////////////////////////////////////////////////////|Cut
            System.out.println("ObjectFabric Generator " + version + " (http://objectfabric.com)");
            System.out.println("Copyright (c) ObjectFabric Inc.");
            System.out.println("");
            boolean java = PlatformAdapter.PLATFORM == CompileTimeSettings.PLATFORM_JAVA;
            String exe = java ? "java -jar objectfabric-" + version + ".jar" : "Generator.exe";
            System.out.println("Usage: " + exe + " [options]");
            System.out.println("");
            System.out.println("Options:");
            System.out.println(Utils.padRight("    " + XML + ":<file.xml>", pad));
            System.out.println(Utils.padRight("", pad) + "XML object model to generate. Default is to look for file");
            System.out.println(Utils.padRight("", pad) + "ObjectModel.xml in current directory.");
            System.out.println(Utils.padRight("    " + OUT + ":<path>", pad));
            System.out.println(Utils.padRight("", pad) + "Target directory for generated source tree. Default is");
            System.out.println(Utils.padRight("", pad) + "current directory.");
            System.out.println(Utils.padRight("    " + TARGET + ":[" + JAVA + "|" + CS + "|" + GWT + "]", pad));
            System.out.println(Utils.padRight("", pad) + "Source language to generate. Default is Java when run on a JVM,");
            System.out.println(Utils.padRight("", pad) + "and C# on .NET.");
            System.out.println(Utils.padRight("    " + NO_SYNC, pad));
            System.out.println(Utils.padRight("", pad) + "Do not generate synchronous methods on generated classes, only the");
            System.out.println(Utils.padRight("", pad) + "asynchronous versions. This is handy to make sure no thread will");
            System.out.println(Utils.padRight("", pad) + "ever block on a method in an application, either for best");
            System.out.println(Utils.padRight("", pad) + "performance, or in environments like GWT where blocking a thread");
            System.out.println(Utils.padRight("", pad) + "is not allowed.");
        }

        return 0;
    }
}

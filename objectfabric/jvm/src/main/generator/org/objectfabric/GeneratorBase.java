/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.objectfabric;

import java.io.File;

public abstract class GeneratorBase {

    public static final String SCHEMA_FILE = "objectfabric-" + TObject.OBJECT_FABRIC_VERSION + ".xsd";

    private ObjectModelDef _model;

    private byte[] _uid;

    private String _xml;

    private String _folder;

    private Target _target;

    private String _copyright;

    private boolean _addGetAndSet;

    private char[] _cache = new char[1024];

    private int _cacheLength;

    public GeneratorBase(ObjectModelDef model) {
        _model = model;
    }

    public ObjectModelDef objectModel() {
        return _model;
    }

    public void setObjectModel(ObjectModelDef value) {
        _model = value;
    }

    public String xml() {
        return _xml;
    }

    public void xml(String value) {
        _xml = value;
    }

    public String folder() {
        return _folder;
    }

    public void folder(String value) {
        _folder = value;
    }

    public Target target() {
        return _target;
    }

    public void target(Target value) {
        _target = value;
    }

    /**
     * ObjectModels are identified using a UID. It can be useful to generate several
     * models with the same UID, e.g. to replicate a model between Java and .NET
     * processes. Models generated using the same UID must be strictly identical, or
     * replication will fail, possibly without any descriptive error message.
     */
    public byte[] objectModelUID() {
        return _uid;
    }

    public void objectModelUID(byte[] value) {
        _uid = value;
    }

    public boolean addGetAndSet() {
        return _addGetAndSet;
    }

    public void addGetAndSet(boolean value) {
        _addGetAndSet = value;
    }

    String copyright() {
        return _copyright;
    }

    void copyright(String value) {
        _copyright = value;
    }

    boolean isJava() {
        return _target == Target.JAVA;
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
            capacity <<= OpenMap.TIMES_TWO_SHIFT;

        if (capacity != _cache.length) {
            char[] temp = new char[capacity];
            Platform.arraycopy(_cache, 0, temp, 0, _cacheLength);
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
        PlatformGenerator.writeFile(file.Path, _cache, _cacheLength);
        _cacheLength = 0;
        System.out.println("Generated " + file.Path);
    }

    public void run(String folder) {
        run(folder, (Target) null);
    }

    public void run(String folder, Target target) {
        run(folder, target, null);
    }

    public void run(String folder, byte[] uid) {
        run(folder, null, uid);
    }

    public void run(String folder, Target target, byte[] uid) {
        run(folder, target, uid, null);
    }

    List<FileGenerator> run(String folder, Target target, byte[] uid, ModelVisitor visitor) {
        if (target == null)
            target = Platform.get().value() == Platform.JVM ? Target.JAVA : Target.CSHARP;

        return target.writeFiles(this, folder, uid, visitor);
    }

    List<FileGenerator> run(byte[] uid, ModelVisitor visitor) {
        if (uid == null)
            uid = Platform.get().newUID();

        if (uid.length != UID.LENGTH)
            throw new IllegalArgumentException();

        _uid = uid;
        _model.prepare();

        if (visitor != null)
            visitor.visit(_model);

        PlatformSet<String> dirs = new PlatformSet<String>();
        List<FileGenerator> files = new List<FileGenerator>();

        for (int i = 0; i < _model.allPackages().size(); i++) {
            PackageDef p = _model.allPackages().get(i);
            String path = folder();

            if (isJava())
                path += "/" + p.fullName().replace('.', '/');

            if (!dirs.contains(path)) {
                File directory = new File(path);
                directory.mkdirs();
                dirs.add(path);
            }

            for (int j = 0; j < p.Classes.size(); j++) {
                FileGenerator file = new FileGeneratorClass(this, p.Classes.get(j));
                write(file);
                files.add(file);
            }
        }

        if (!objectModel().skip()) {
            FileGenerator file = new FileGeneratorObjectModel(this);
            write(file);
            files.add(file);
        }

        return files;
    }

    /**
     * Deletes all files and folders. Can be used before running the generator.
     */
    public static void clearFolder(String folder) {
        PlatformGenerator.clearFolder(folder);
    }

    int parseArgs(String[] args) {
        final String XML = "-xml:";
        final String OUT = "-out:";
        final String LANG = "-lang:";
        final String JAVA = "java";
        final String CS = "cs";

        for (String arg : args) {
            if (arg.startsWith(XML)) {
                xml(arg.substring(XML.length()));

                if (!PlatformGenerator.fileExists(xml())) {
                    System.out.println("Input file does not exist: " + xml());
                    return 1;
                }
            } else if (arg.startsWith(OUT)) {
                folder(arg.substring(OUT.length()));
                PlatformGenerator.mkdir(folder());
            } else if (arg.startsWith(LANG)) {
                String t = arg.substring(LANG.length());

                if (JAVA.equals(t))
                    target(Target.JAVA);
                else if (CS.equals(t))
                    target(Target.CSHARP);
                else {
                    System.out.println("Invalid target: " + arg);
                    return 1;
                }
            } else {
                System.out.println("Invalid argument: " + arg);
                return 1;
            }
        }

        if (xml() == null || folder() == null) {
            int pad = 12;

            System.out.println("");// ////////////////////////////////////////////////////////////////////////////////////////|Cut
            System.out.println("ObjectFabric Generator " + TObject.OBJECT_FABRIC_VERSION + " (http://objectfabric.org)");
            System.out.println("");
            boolean java = Platform.get().value() == Platform.JVM;
            String exe = java ? "java -jar objectfabric.jar" : "Generator.exe";
            System.out.println("Usage: " + exe + " [options]");
            System.out.println("");
            System.out.println("Options:");
            System.out.println(Utils.padRight("    " + XML + ":<file.xml>", pad));
            System.out.println(Utils.padRight("", pad) + "XML object model to generate. Default is to look for file");
            System.out.println(Utils.padRight("", pad) + "ObjectModel.xml in current directory.");
            System.out.println(Utils.padRight("    " + OUT + ":<path>", pad));
            System.out.println(Utils.padRight("", pad) + "Target directory for generated source tree. Default is");
            System.out.println(Utils.padRight("", pad) + "current directory.");
            System.out.println(Utils.padRight("    " + LANG + ":[" + JAVA + "|" + CS + "]", pad));
            System.out.println(Utils.padRight("", pad) + "Source language to generate. Default is Java when run on a JVM,");
            System.out.println(Utils.padRight("", pad) + "and C# on .NET.");
        }

        return 0;
    }
}

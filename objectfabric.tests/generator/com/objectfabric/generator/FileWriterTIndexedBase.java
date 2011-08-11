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
import java.io.FileReader;

import com.objectfabric.generator.FileGenerator;
import com.objectfabric.generator.Generator;
import com.objectfabric.misc.Utils;

class FileWriterTIndexedBase extends FileGenerator {

    public FileWriterTIndexedBase(Generator generator, String packag) {
        super(generator, packag, "TIndexedBase");
    }

    @Override
    protected void header() {
        copyright();

        wl("package " + Package + ";");
        wl();
        wl("import com.objectfabric.misc.*;");
        wl();

        warning();
    }

    @Override
    protected void body() {
        wl("class TIndexedBase extends TObject.UserTObject {");
        wl();
        wl("    protected TIndexedBase(TObject.Version shared, Transaction trunk) {");
        wl("        super(shared, trunk);");
        wl("    }");
        wl();

        StringBuilder model;

        try {
            File file = new File("generator/com/objectfabric/generator/TIndexedBase.txt");
            FileReader reader = new FileReader(file);
            char[] chars = new char[(int) file.length()];
            reader.read(chars);
            model = new StringBuilder(new String(chars));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        // Indexed 32
        {
            StringBuilder copy = new StringBuilder(model.toString());
            Utils.replace(copy, "%name%", "TIndexed32");
            write(copy);
            wl();
            wl();
        }

        // Indexed N
        {
            StringBuilder copy = new StringBuilder(model.toString());
            Utils.replace(copy, "%name%", "TIndexedN");
            write(copy);
            wl();
        }

        wl("}");
    }
}

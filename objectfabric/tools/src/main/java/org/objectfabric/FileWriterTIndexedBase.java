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
import java.io.FileReader;

class FileWriterTIndexedBase extends FileGenerator {

    public FileWriterTIndexedBase(Generator generator, String packag) {
        super(generator, packag, "TIndexedBase");
    }

    @Override
    protected void header() {
        copyright();

        wl("package " + Package + ";");
        wl();

        warning();
    }

    @Override
    protected void body() {
        wl("abstract class TIndexedBase extends TObject {");
        wl();
        wl("    protected TIndexedBase(Resource resource, TObject.Version shared) {");
        wl("        super(resource, shared);");
        wl("    }");
        wl();

        StringBuilder model;

        try {
            File file = new File("src/main/java/org/objectfabric/TIndexedBase.txt");
            FileReader reader = new FileReader(file);
            char[] chars = new char[(int) file.length()];
            reader.read(chars);
            reader.close();
            model = new StringBuilder(new String(chars));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        // 32
        {
            StringBuilder copy = new StringBuilder(model.toString());
            Utils.replace(copy, "%name%", "32");
            write(copy);
            wl();
            wl();
        }

        // N
        {
            StringBuilder copy = new StringBuilder(model.toString());
            Utils.replace(copy, "%name%", "N");
            write(copy);
            wl();
        }

        wl("}");
    }
}

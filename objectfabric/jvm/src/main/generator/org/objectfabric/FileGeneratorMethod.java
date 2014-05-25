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

// TODO: send only arguments delta between two calls to same method
class FileGeneratorMethod {

    private final GeneratorBase _gen;

    private final FileGeneratorClass _writer;

    private final MethodDef _method;

    FileGeneratorMethod(FileGeneratorClass writer, MethodDef method) {
        _gen = writer.g();
        _writer = writer;
        _method = method;
    }

    void writeCalls() {
        String args = "";
        String argsNames = "";

        for (int i = 0; i < _method.Arguments.size(); i++) {
            String name = _method.Arguments.get(i).Name;
            String arg = _method.Arguments.get(i).type().fullName(_gen.target(), true) + " " + name;

            if (args.length() == 0) {
                args += arg;
                argsNames += name;
            } else {
                args += ", " + arg;
                argsNames += ", " + name;
            }
        }

        String final_ = _gen.isJava() ? "final " : "";
        String generic = "";

        if (_method.ReturnValue.type().otherClass() == Platform.get().voidClass())
            generic += _gen.isJava() ? "<java.lang.Void>" : "";
        else
            generic += "<" + _method.ReturnValue.type().fullNameWithGenericsAndBoxPrimitives(_gen.target()) + ">";

        String name = _method.nameWithRightCaps(_gen);
        String comma = args.length() > 0 ? ", " : "";
        String visibility = _method.Public ? "public" : "protected";
        String returnType = _method.ReturnValue.type().fullName(_gen.target(), true);
        String returnVarType = returnType;
        String returnStatement = "return ";
        String executor = _gen.isJava() ? "java.util.concurrent.Executor" : "System.Threading.Tasks.TaskScheduler";
        String future = _gen.isJava() ? "java.util.concurrent.Future" : "System.Threading.Tasks.Task";
        boolean returnVoid = false;

        if (_method.ReturnValue.type().otherClass() == Platform.get().voidClass()) {
            returnVarType = "java.lang.Void";
            returnStatement = "";
            returnVoid = true;
        }

        wl();

        writeComments("");

        wl("    " + visibility + " " + final_ + returnType + " " + name + "(" + args + ") {");
        wl("        " + returnStatement + name + "(" + argsNames + comma + "getMethodExecutor());");
        wl("    }");
        wl();

        writeComments("Specifies the Executor or TaskScheduler that will run the method. For replicated objects, the method runs by default on the site the object has been created, using site's method executor.");

        wl("    " + visibility + " " + final_ + returnType + " " + name + "(" + args + comma + executor + " executor) {");
        wl("        if (executor == getTransparentExecutor_())");
        wl("            " + returnStatement + name + "Implementation(" + argsNames + ");");
        wl("        else {");
        wl("            " + future + generic + " future_ = " + name + "Async(" + argsNames + comma + (_gen.isJava() ? "getNopCallback_(), null, " : "") + "executor);");

        if (_gen.isJava()) {
            wl();
            wl("            try {");
            wl("                " + returnStatement + "future_.get();");
            wl("            } catch (java.lang.InterruptedException ex_) {");
            wl("                throw new java.lang.RuntimeException(ex_);");
            wl("            } catch (java.util.concurrent.ExecutionException ex_) {");
            wl("                if (ex_.getCause() instanceof java.lang.RuntimeException)");
            wl("                    throw (java.lang.RuntimeException) ex_.getCause();");
            wl();
            wl("                throw new java.lang.RuntimeException(ex_.getCause());");
            wl("            }");
        } else {
            wl("            " + returnStatement + "future_.Result;");
        }

        wl("        }");
        wl("    }");
        wl();

        writeComments("Asynchronous version.");

        String callback = _gen.isJava() ? comma + "org.objectfabric.AsyncCallback" + generic + " callback" : "";

        wl("    " + visibility + " " + final_ + future + generic + " " + name + "Async(" + args + callback + ") {");
        wl("        return " + name + "Async(" + argsNames + comma + (_gen.isJava() ? "callback, " : "") + "getMethodExecutor());");
        wl("    }");
        wl();

        writeComments("Asynchronous version, and specifies the Executor or TaskScheduler that will run the method. For replicated objects,",
                "the method runs by default on the site the object has been created. @see Site.getMethodExecutor() to specify an execution site.");

        String asyncArgs;

        if (_gen.isJava())
            asyncArgs = args + comma + "org.objectfabric.AsyncCallback" + generic + " callback, ";
        else
            asyncArgs = args + comma;

        wl("    " + visibility + " " + final_ + future + generic + " " + name + "Async(" + asyncArgs + executor + " executor) {");
        wl("        if (executor == getTransparentExecutor_()) {");
        wl("            " + returnVarType + " result_ = " + _method.ReturnValue.type().defaultString() + ";");
        wl("            java.lang.Exception exception_ = null;");
        wl();
        wl("            try {");
        wl("                " + (returnVoid ? "" : "result_ = ") + name + "Implementation(" + argsNames + ");");
        wl("            } catch (java.lang.Exception e_) {");
        wl("                exception_ = e_;");
        wl("            }");
        wl();
        wl("            return getCompletedFuture_(result_, exception_" + (_gen.isJava() ? ", callback" : "") + ");");
        wl("        } else {");

        String type = _method.fullType(_gen);
        wl("            " + type + " instance_ = " + type + ".getOrCreateInstance(getFabric());");
        wl("            " + type + ".Version version_ = (" + type + ".Version) getSharedVersion_(instance_);");
        wl("            version_.clearBits();");

        for (int i = 0; i < _method.Arguments.size(); i++) {
            ArgumentDef arg = _method.Arguments.get(i);

            wl("            version_._" + arg.Name + " = " + arg.Name + ";");
            wl("            version_.setBit(" + _method.fullType(_gen) + "." + arg.nameAsConstant() + "_INDEX);");
        }

        if (_gen.isJava()) {
            String methodArgs = "this, instance_, METHOD_" + _method.indexInClass() + ", callback";
            wl("            org.objectfabric.TObject.UserTObject.LocalMethodCall call_ = new org.objectfabric.TObject.UserTObject.LocalMethodCall(" + methodArgs + ");");
        } else
            wl("            LocalMethodCall" + generic + " call_ = new LocalMethodCall" + generic + "(this, instance_, METHOD_" + _method.indexInClass() + ", callback, asyncOptions);");

        if (_gen.isJava())
            wl("            executor.execute(call_);");
        else
            wl("            call_.Task.Start( executor );");

        wl("            return call_" + (_gen.isCSharp() ? ".Task" : "") + ";");
        wl("        }");
        wl("    }");

        if (_gen.isJava()) {
            wl();
            wl("    /**");
            wl("     * Override to implement the method.");
            wl("     */");
            wl("    protected " + _method.ReturnValue.type().fullName(_gen.target(), true) + " " + name + "Implementation(" + args + ") {");
            wl("        throw new java.lang.RuntimeException(org.objectfabric.Strings.MISSING_METHOD_CALL_IMPLEMENTATION);");
            wl("    }");
            wl();
            wl("    /**");
            wl("     * Override to implement the method asynchronously.");
            wl("     */");
            wl("    protected void " + name + "ImplementationAsync(" + args + comma + "org.objectfabric.MethodCall call) {");
            wl("        try {");

            if (returnVoid) {
                wl("            " + name + "Implementation(" + argsNames + ");");
                wl("            call.set(null);");
            } else
                wl("            call.set(" + name + "Implementation(" + argsNames + "));");

            wl("        } catch (java.lang.Exception e_) {");
            wl("            call.setException(e_);");
            wl("        }");
            wl("    }");
        }
    }

    private void writeComments(String... lines) {
        wl("    /**");

        if (_method.Comment != null && _method.Comment.length() > 0) {
            wl("     * " + _method.Comment);
            wl("     * <nl>");
        }

        for (String line : lines)
            wl("     * " + line);

        wl("     */");
    }

    void writeInvocation() {
        String args = "";

        if (_method.Arguments.size() > 0) {
            wl("                " + _method.fullType(_gen) + ".Version version_ = (" + _method.fullType(_gen) + ".Version) getMethodVersion_(call);");

            for (int i = 0; i < _method.Arguments.size(); i++) {
                String type = _method.Arguments.get(i).type().fullName(_gen.target(), true);
                String name = _method.Arguments.get(i).Name;
                String default_ = _method.Arguments.get(i).type().defaultString();
                wl("                " + type + " " + name + " = version_ != null ? (" + type + ") version_._" + name + " : " + default_ + ";");
                args += name + ", ";
            }

            wl();
        }

        wl("                try {");
        wl("                    " + _method.nameWithRightCaps(_gen) + "ImplementationAsync(" + args + "call);");
        wl("                } catch (java.lang.Exception e_) {");
        wl("                    call.setException(e_);");
        wl("                }");
        wl();
    }

    void writeResult() {
        wl("                " + _method.fullType(_gen) + ".Version version = (" + _method.fullType(_gen) + ".Version) getMethodVersion_(call);");
        wl();
        wl("                if (version.__error == null)");

        if (_method.ReturnValue.type().otherClass() != Platform.get().voidClass())
            wl("                    call.set(version.__return);");
        else
            wl("                    call.set(null);");

        wl("                else");
        wl("                    call.setException(new org.objectfabric.ReplicatedException(version.__error));");
    }

    private void wl(String line) {
        _writer.wl(line);
    }

    private void wl() {
        _writer.wl();
    }
}

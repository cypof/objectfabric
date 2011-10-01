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

import com.objectfabric.generator.Generator.Flag;
import com.objectfabric.misc.PlatformClass;

// TODO: send only arguments delta between two calls to same method
class FileGeneratorMethod {

    protected final Generator _gen;

    private final FileGeneratorClass _writer;

    private final MethodDef _method;

    protected FileGeneratorMethod(FileGeneratorClass writer, MethodDef method) {
        _gen = writer.g();
        _writer = writer;
        _method = method;
    }

    protected void writeCalls() {
        String args = "";
        String argsNames = "";

        for (int i = 0; i < _method.Arguments.size(); i++) {
            String name = _method.Arguments.get(i).Name;
            String arg = _method.Arguments.get(i).getType().getFullName(_gen.getTarget(), true) + " " + name;

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

        if (_method.ReturnValue.getType().getOtherClass() == PlatformClass.getVoidClass())
            generic += _gen.isJava() ? "<java.lang.Void>" : "";
        else
            generic += "<" + _method.ReturnValue.getType().getFullNameWithGenericsAndBoxPrimitives(_gen.getTarget()) + ">";

        String name = _method.getNameWithRightCaps(_gen);
        String comma = args.length() > 0 ? ", " : "";
        String visibility = _method.Public ? "public" : "protected";
        String execVisibility = _method.NoCustomExecutor ? "private" : visibility;
        String returnType = _method.ReturnValue.getType().getFullName(_gen.getTarget(), true);
        String returnVarType = returnType;
        String returnStatement = "return ";
        String executor = _gen.isJava() ? "java.util.concurrent.Executor" : "System.Threading.Tasks.TaskScheduler";
        String future = _gen.isJava() ? "java.util.concurrent.Future" : "System.Threading.Tasks.Task";
        boolean returnVoid = false;

        if (_method.ReturnValue.getType().getOtherClass() == PlatformClass.getVoidClass()) {
            returnVarType = "java.lang.Void";
            returnStatement = "";
            returnVoid = true;
        }

        if (!_gen.getFlags().contains(Flag.GENERATE_ONLY_ASYNCHRONOUS_METHODS)) {
            wl();

            writeComments("");

            wl("    " + visibility + " " + final_ + returnType + " " + name + "(" + args + ") {");
            wl("        " + returnStatement + name + "(" + argsNames + comma + "getDefaultMethodExecutor_objectfabric());");
            wl("    }");
            wl();

            writeComments("Specifies the Executor or TaskScheduler that will run the method. For replicated objects, the method runs by default on the site the object has been created, using site's method executor.");

            wl("    " + execVisibility + " " + final_ + returnType + " " + name + "(" + args + comma + executor + " executor) {");
            wl("        if (executor == com.objectfabric.misc.Transparent" + (_gen.isJava() ? "Executor.getInstance()" : "TaskScheduler.Instance") + ")");
            wl("            " + returnStatement + name + "Implementation(" + argsNames + ");");
            wl("        else {");
            wl("            " + future + generic + " future_ = " + name + "Async(" + argsNames + comma + (_gen.isJava() ? "getNopCallback_objectfabric(), null, " : "") + "executor);");

            if (_gen.isJava()) {
                wl();
                wl("            try {");
                wl("                " + returnStatement + "future_.get();");
                wl("            } catch (java.lang.InterruptedException ex_) {");
                wl("                throw new RuntimeException(ex_);");
                wl("            } catch (java.util.concurrent.ExecutionException ex_) {");
                wl("                if (ex_.getCause() instanceof RuntimeException)");
                wl("                    throw (RuntimeException) ex_.getCause();");
                wl();
                wl("                throw new RuntimeException(ex_.getCause());");
                wl("            }");
            } else {
                wl("            " + returnStatement + "future_.Result;");
            }

            wl("        }");
            wl("    }");
            wl();
        }

        writeComments("Asynchronous version.");

        String callback = _gen.isJava() ? comma + "com.objectfabric.misc.AsyncCallback" + generic + " callback" : "";

        wl("    " + visibility + " " + final_ + future + generic + " " + name + "Async(" + args + callback + ") {");
        wl("        return " + name + "Async(" + argsNames + comma + (_gen.isJava() ? "callback, getDefaultAsyncOptions_objectfabric(), " : "") + "getDefaultMethodExecutor_objectfabric());");
        wl("    }");
        wl();

        if (_gen.isJava()) {
            writeComments("Asynchronous version, with options for the callback.");

            wl("    " + visibility + " " + final_ + future + generic + " " + name + "Async(" + args + callback + ", com.objectfabric.AsyncOptions asyncOptions) {");
            wl("        return " + name + "Async(" + argsNames + comma + "callback, asyncOptions, getDefaultMethodExecutor_objectfabric());");
            wl("    }");
            wl();
        }

        writeComments("Asynchronous version, with options for the callback, and specifies the Executor or TaskScheduler that will run the method. For replicated objects,",
                "the method runs by default on the site the object has been created. @see Site.getMethodExecutor() to specify an execution site.");

        String asyncArgs;

        if (_gen.isJava())
            asyncArgs = args + comma + "com.objectfabric.misc.AsyncCallback" + generic + " callback, com.objectfabric.AsyncOptions asyncOptions, ";
        else
            asyncArgs = args + comma;

        wl("    " + execVisibility + " " + final_ + future + generic + " " + name + "Async(" + asyncArgs + executor + " executor) {");

        if (_gen.isJava())
            wl("        if (executor == com.objectfabric.misc.TransparentExecutor.getInstance()) {");
        else
            wl("        if (executor == ObjectFabric.TransparentTaskScheduler.Instance) {");

        wl("            " + returnVarType + " result_ = " + _method.ReturnValue.getType().getDefaultString() + ";");
        wl("            java.lang.Exception exception_ = null;");
        wl();
        wl("            try {");
        wl("                " + (returnVoid ? "" : "result_ = ") + name + "Implementation(" + argsNames + ");");
        wl("            } catch (java.lang.Exception e_) {");
        wl("                exception_ = e_;");
        wl("            }");
        wl();
        wl("            return getCompletedFuture_objectfabric(result_, exception_" + (_gen.isJava() ? ", callback, asyncOptions" : "") + ");");
        wl("        } else {");

        wl("            " + _method.getFullType(_gen) + ".Version version_ = (" + _method.getFullType(_gen) + ".Version) createVersion_objectfabric(" + _method.getFullType(_gen) + ".INSTANCE);");
        wl();

        for (int i = 0; i < _method.Arguments.size(); i++) {
            ArgumentDef arg = _method.Arguments.get(i);

            wl("            if (" + arg.Name + " != " + arg.getType().getDefaultString() + ") {");
            wl("                version_._" + arg.Name + " = " + arg.Name + ";");
            wl("                version_.setBit(" + _method.getFullType(_gen) + "." + arg.getNameAsConstant() + "_INDEX);");
            wl("            }");
            wl();
        }

        if (_gen.isJava())
            wl("            com.objectfabric.TObject.UserTObject.LocalMethodCall call_ = new com.objectfabric.TObject.UserTObject.LocalMethodCall(this, " + _method.getFullType(_gen) + ".INSTANCE, version_, METHOD_"
                    + _method.getIndexInClass() + ", callback, asyncOptions);");
        else
            wl("            LocalMethodCall" + generic + " call_ = new LocalMethodCall" + generic + "(this, " + _method.getFullType(_gen) + ".INSTANCE, version_, METHOD_" + _method.getIndexInClass() + ");");

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
            wl("    protected " + _method.ReturnValue.getType().getFullName(_gen.getTarget(), true) + " " + name + "Implementation(" + args + ") {");
            wl("        throw new RuntimeException(com.objectfabric.Strings.MISSING_METHOD_CALL_IMPLEMENTATION);");
            wl("    }");
            wl();
            wl("    /**");
            wl("     * Override to implement the method asynchronously.");
            wl("     */");
            wl("    protected void " + name + "ImplementationAsync(" + args + comma + "com.objectfabric.MethodCall call) {");
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

    protected void writeInvocation() {
        String args = "";

        if (_method.Arguments.size() > 0) {
            wl("                " + _method.getFullType(_gen) + ".Version version_ = (" + _method.getFullType(_gen) + ".Version) getMethodVersion_objectfabric(call);");

            for (int i = 0; i < _method.Arguments.size(); i++) {
                String type = _method.Arguments.get(i).getType().getFullName(_gen.getTarget(), true);
                String name = _method.Arguments.get(i).Name;
                String default_ = _method.Arguments.get(i).getType().getDefaultString();
                wl("                " + type + " " + name + " = version_ != null ? (" + type + ") version_._" + name + " : " + default_ + ";");
                args += name + ", ";
            }

            wl();
        }

        wl("                try {");
        wl("                    " + _method.getNameWithRightCaps(_gen) + "ImplementationAsync(" + args + "call);");
        wl("                } catch (java.lang.Exception e_) {");
        wl("                    call.setException(e_);");
        wl("                }");
        wl();
    }

    protected void writeResult() {
        wl("                " + _method.getFullType(_gen) + ".Version version = (" + _method.getFullType(_gen) + ".Version) getMethodVersion_objectfabric(call);");
        wl();
        wl("                if (version == null || version._error_objectfabric == null)");

        if (_method.ReturnValue.getType().getOtherClass() != PlatformClass.getVoidClass())
            wl("                    setDirect(call, version != null ? version._return_objectfabric : null);");
        else
            wl("                    setDirect(call, null);");

        wl("                else");
        wl("                    setExceptionDirect(call, new com.objectfabric.misc.ReplicatedException(version._error_objectfabric));");
    }

    private void wl(String line) {
        _writer.wl(line);
    }

    private void wl() {
        _writer.wl();
    }
}

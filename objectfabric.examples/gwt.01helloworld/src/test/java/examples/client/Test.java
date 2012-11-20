///**
// * This file is part of ObjectFabric (http://objectfabric.org).
// *
// * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
// * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
// * 
// * Copyright ObjectFabric Inc.
// * 
// * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
// * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
// */
//
//package examples.client;
//
//import junit.framework.Assert;
//
//import org.objectfabric.AsyncCallback;
//import org.objectfabric.GWTWorkspace;
//import org.objectfabric.Resource;
//import org.objectfabric.WebSocketURIHandler;
//import org.objectfabric.Workspace;
//
//import com.google.gwt.junit.client.GWTTestCase;
//
//public class Test extends GWTTestCase {
//
//    @Override
//    public String getModuleName() {
//        return Main.class.getName();
//    }
//
//    public void test() {
//        delayTestFinish(1000);
//
//        final Workspace workspace = new GWTWorkspace();
//        workspace.addURIHandler(new WebSocketURIHandler());
//        Resource resource = workspace.resolve(Main.URI);
//
//        resource.getAsync(new AsyncCallback<Object>() {
//
//            @Override
//            public void onSuccess(Object result) {
//                Assert.assertEquals("Hello World!", result);
//                workspace.close();
//                finishTest();
//            }
//
//            @Override
//            public void onFailure(Exception e) {
//            }
//        });
//    }
//}
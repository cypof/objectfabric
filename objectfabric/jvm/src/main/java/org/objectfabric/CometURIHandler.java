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
//package org.objectfabric;
//
///**
// * Long polling over HTTP.
// */
//public class CometURIHandler extends ClientURIHandler {
//
//    public static final String SCHEME_HTTP = "http", SCHEME_HTTPS = "https";
//
//    private static final CometURIHandler _instance = new CometURIHandler();
//
//    static CometURIHandler getInstance() {
//        return _instance;
//    }
//
//    private CometURIHandler() {
//    }
//
//    @Override
//    public URI handle(Address address, String path) {
//        if (address.Host != null && address.Host.length() > 0) {
//            String s = address.Scheme;
//
//            if (SCHEME_HTTP.equals(s) || SCHEME_HTTPS.equals(s)) {
//                Remote remote = get(address);
//                return remote.getURI(path);
//            }
//        }
//
//        return null;
//    }
//
//    @Override
//    Remote createRemote(Address address) {
//        return new CometRemote(address);
//    }
//}

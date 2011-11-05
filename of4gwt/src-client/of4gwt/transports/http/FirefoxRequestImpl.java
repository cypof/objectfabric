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

package of4gwt.transports.http;

final class FirefoxRequestImpl extends XMLHttpRequestImpl {

    /**
     * Special case for Firefox 3 that does not seem to like 0 bytes in POST.
     */
    private static final boolean FIREFOX_3;

    public static native String getUserAgent() /*-{
		return navigator.userAgent.toLowerCase();
    }-*/;

    static {
        String agent = getUserAgent().toLowerCase();
        FIREFOX_3 = agent.contains("firefox/3");
    }

    protected FirefoxRequestImpl() {
    }

    @Override
    String encode(byte[] data, int length) {
        if (FIREFOX_3)
            return XDomainRequestImpl.encode(data, length, CometTransport.ENCODING_PADDING);

        return super.encode(data, length);
    }
}

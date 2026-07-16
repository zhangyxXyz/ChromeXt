package org.matrix.chromext.bridge;

import org.matrix.chromext.bridge.IChromeXtBrowser;

interface IChromeXtBridge {
    void registerBrowser(String packageName, IChromeXtBrowser browser);
}

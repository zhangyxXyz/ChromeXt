package org.matrix.chromext.bridge;

import android.os.ParcelFileDescriptor;

interface IChromeXtBrowser {
    void writeSnapshot(in ParcelFileDescriptor destination);
    void restoreSnapshot(in ParcelFileDescriptor source, in ParcelFileDescriptor resultDestination);
    void writeResponse(String action, String payload, in ParcelFileDescriptor destination);
    void writeStreamResponse(String action, in ParcelFileDescriptor payloadSource, in ParcelFileDescriptor destination);
}

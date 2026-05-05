package org.mockserver.testing.closurecallback;

import org.mockserver.closurecallback.websocketregistry.LocalCallbackRegistry;

public class ViaWebSocket {

    public static void viaWebSocket(RunnableThatThrows runnableThatThrows) throws Exception {
        try {
            LocalCallbackRegistry.enabled = false;
            runnableThatThrows.run();
        } finally {
            LocalCallbackRegistry.enabled = true;
        }
    }

    public interface RunnableThatThrows {
        void run() throws Exception;
    }

}

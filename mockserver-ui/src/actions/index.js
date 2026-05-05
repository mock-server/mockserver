export const CONNECT_SOCKET = 'CONNECT_SOCKET';

const connectWebSocket = (host, port, secure, contextPath) => ({
    type: CONNECT_SOCKET,
    host: host,
    port: port,
    secure: secure,
    contextPath: contextPath
});

export const connectSocket = (host, port, secure, contextPath) => (dispatch) => {
    return dispatch(connectWebSocket(host, port, secure, contextPath));
};

export const SEND_MESSAGE = 'SEND_MESSAGE';

const sendWebSocketMessage = (message, host, port, secure) => ({
    type: SEND_MESSAGE,
    message: message,
    host: host,
    port: port,
    secure: secure
});

export const sendMessage = (message, host, port, secure) => (dispatch) => {
    return dispatch(sendWebSocketMessage(message, host, port, secure));
};

export const MESSAGE_RECEIVED = 'MESSAGE_RECEIVED';

export const webSocketMessageReceived = (message) => ({
    type: MESSAGE_RECEIVED,
    entities: message
});

export const DISCONNECT_SOCKET = 'DISCONNECT_SOCKET';

const disconnectWebSocket = () => ({
    type: DISCONNECT_SOCKET
});

export const disconnectSocket = () => (dispatch) => {
    return dispatch(disconnectWebSocket());
};

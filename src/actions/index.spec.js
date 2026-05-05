import configureMockStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import * as SocketActions from '../actions';
import fetchMock from 'fetch-mock';
import expect from 'expect'

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe('async actions', () => {
    afterEach(() => {
        fetchMock.reset();
        fetchMock.restore();

        // fetchMock
        //     .getOnce('/todos', { body: { todos: ['do something'] }, headers: { 'content-type': 'application/json' } });
    });

    it('should create CONNECT_SOCKET', () => {
        // given
        let host = "random.host";
        let port = "666";
        let contextPath = "";

        const expectedActions = [
            {type: SocketActions.CONNECT_SOCKET, contextPath, host, port}
        ];
        const store = mockStore({requestMatcher: {}});

        // when
        store.dispatch(SocketActions.connectSocket(host, port, contextPath));

        // then
        expect(store.getActions()).toEqual(expectedActions);
    });

    it('should create SEND_MESSAGE', () => {
        // given
        let message = "some message";
        let host = "random.host";
        let port = "666";

        const expectedActions = [
            {type: SocketActions.SEND_MESSAGE, message, host, port}
        ];
        const store = mockStore({requestMatcher: {}});

        // when
        store.dispatch(SocketActions.sendMessage(message, host, port));

        // then
        expect(store.getActions()).toEqual(expectedActions);
    });

    it('should create MESSAGE_RECEIVED', () => {
        // given
        let entities = {};

        const expectedActions = {type: SocketActions.MESSAGE_RECEIVED, entities};

        const store = mockStore({requestMatcher: {}});

        // when
        const action = SocketActions.webSocketMessageReceived(entities);

        // then
        expect(action).toEqual(expectedActions);
    });

    it('should create DISCONNECT_SOCKET', () => {
        // given
        const expectedActions = [
            {type: SocketActions.DISCONNECT_SOCKET}
        ];
        const store = mockStore({requestMatcher: {}});

        // when
        store.dispatch(SocketActions.disconnectSocket());

        // then
        expect(store.getActions()).toEqual(expectedActions);
    });
});
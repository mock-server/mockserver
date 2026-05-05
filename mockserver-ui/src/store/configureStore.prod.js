import {applyMiddleware, createStore} from 'redux'
import thunk from 'redux-thunk'
import rootReducer from '../reducers'
import socket from '../middleware/socket'
import exposeState from "../middleware/exposeState";

const configureStore = preloadedState => {
    return createStore(
        rootReducer,
        preloadedState,
        applyMiddleware(thunk, socket, exposeState)
    )
};

export default configureStore

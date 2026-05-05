import {MESSAGE_RECEIVED} from '../actions'

const entities = (state = {
    activeExpectations: [],
    proxiedRequests: [],
    recordedRequests: [],
    logMessages: []
}, action) => {
    if (action.type === MESSAGE_RECEIVED && action.entities) {
        return {
            activeExpectations: action.entities.activeExpectations ? action.entities.activeExpectations : [],
            proxiedRequests: action.entities.proxiedRequests ? action.entities.proxiedRequests : [],
            recordedRequests: action.entities.recordedRequests ? action.entities.recordedRequests : [],
            logMessages: action.entities.logMessages
        };
    }
    return state;
};

export default entities
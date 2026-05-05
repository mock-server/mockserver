import {combineReducers} from 'redux'
import {reducer as form} from 'redux-form'
import entities from './entities'

const rootReducer = combineReducers({
    entities,
    form,
});

export default rootReducer
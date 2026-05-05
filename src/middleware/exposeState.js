export default store => next => action => {
    next({ ...action, getState: store.getState });
}
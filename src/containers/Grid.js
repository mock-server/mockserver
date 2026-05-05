import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {connect} from 'react-redux';
import LogList from "../containers/LogList";
import JsonList from "../containers/JsonList";
import './grid.css';

class Grid extends Component {
    static propTypes = {
        entities: PropTypes.shape({
            activeExpectations: PropTypes.array.isRequired,
            proxiedRequests: PropTypes.array.isRequired,
            recordedRequests: PropTypes.array.isRequired,
            logMessages: PropTypes.array.isRequired,
        }).isRequired,
        requestFilter: PropTypes.object.isRequired,
    };

    render() {
        const {
            entities: {
                activeExpectations = [],
                proxiedRequests = [],
                recordedRequests = [],
                logMessages = [],
            },
        } = this.props;
        return (
            <div style={{
                margin: "1%",
                width: "98%"
            }}>
                <div className="row" style={{
                    borderStyle: "dashed",
                    borderWidth: "1px",
                    margin: "15px 0",
                    padding: "17px 17px",
                    minWidth: "1100px",
                }}>
                    <LogList items={logMessages}
                             header={"Log Messages (most recent at the top)"}/>
                </div>
                <div className="row" style={{
                    borderStyle: "dashed",
                    borderWidth: "1px",
                    margin: "15px 0",
                    padding: "17px 17px",
                    minWidth: "1100px",
                }}>
                    <JsonList items={activeExpectations}
                              header={"Active Expectations (in the order they are applied)"}
                              reverseIndex={false}/>
                </div>
                <div className="row" style={{
                    borderStyle: "dashed",
                    borderWidth: "1px",
                    margin: "15px 0",
                    padding: "17px 17px",
                    minWidth: "1100px",
                }}>
                    <div style={{
                        width: "49%",
                        minWidth: "500px",
                        float: "left",
                        padding: "0",
                        paddingRight: "1%",
                        borderRightStyle: "dashed",
                        borderRightWidth: "1px",
                    }}>
                        <JsonList items={recordedRequests}
                                  header={"Received Requests (most recent at the top)"}/>
                    </div>
                    <div style={{
                        width: "49%",
                        minWidth: "500px",
                        float: "right"
                    }}>
                        <JsonList items={proxiedRequests}
                                  header={"Proxied Requests (most recent at the top)"}/>
                    </div>
                </div>
            </div>
        );
    }
}

const mapStateToProps = (state) => {
    const {
        activeExpectations = [],
        proxiedRequests = [],
        recordedRequests = [],
        logMessages = [],
    } = state.entities;

    const {
        requestFilter = {}
    } = state;

    return {
        entities: {
            activeExpectations,
            proxiedRequests,
            recordedRequests,
            logMessages,
        },
        requestFilter
    }
};

const mapDispatchToProps = {};

export default connect(mapStateToProps, mapDispatchToProps)(Grid)

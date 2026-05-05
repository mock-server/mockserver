import React, {Component} from 'react';
import PropTypes from 'prop-types';
import JsonItem from "./JsonItem";
import './log.css';

export default class LogMessage extends Component {
    static propTypes = {
        logMessage: PropTypes.oneOfType([PropTypes.object.isRequired, PropTypes.array.isRequired]).isRequired,
        group: PropTypes.object
    };

    render() {

        function addLinks(value) {
            var urlMatch = value.match("(http){1}s?://[^\\s]*");
            if (urlMatch) {
                let matchedUrl = urlMatch[0];
                return (<span>{value.substr(0, value.indexOf(matchedUrl))}<a style={{
                    textDecoration: "underline",
                    color: "rgb(95, 113, 245)"
                }} href={matchedUrl} target="_blank" rel="noopener noreferrer">{matchedUrl}</a>{value.substr(value.indexOf(matchedUrl) + matchedUrl.length)}</span>);
            } else {
                return value;
            }
        }

        const {
            logMessage = {},
            group = false,
            indent = false,
        } = this.props;

        function renderItem(logMessage) {
            let cellStyle = {
                display: "table-cell",
                fontFamily: "monospace, Roboto, sans-serif"
            };
            return <div style={Object.assign({
                paddingLeft: "5px",
                paddingRight: "5px",
                fontSize: indent ? "0.9em" : "1.0em"
            }, logMessage.style)}>
                <div style={Object.assign({
                    whiteSpace: "pre",
                    position: "relative",
                }, cellStyle)}>{logMessage.description}</div>
                {logMessage.messageParts ? logMessage.messageParts.map((messagePart) => {
                    if (messagePart.value) {
                        if (!messagePart.argument) {
                            return <div key={messagePart.key}
                                        style={cellStyle}>{addLinks(messagePart.value)}</div>;
                        } else {
                            if (messagePart.multiline || messagePart.because) {
                                let line = messagePart.value.map(
                                    (reason, index) => {
                                        let color = "rgb(255, 255, 255)";
                                        if (messagePart.because) {
                                            if (reason.indexOf("matched") !== -1) {
                                                color = "rgb(107, 199, 118)";
                                            } else if (reason.indexOf("didn't match") !== -1) {
                                                color = "rgb(216, 88, 118)";
                                            } else {
                                                color = "rgb(255, 255, 255)";
                                            }
                                        }
                                        return <span key={messagePart.key + "_" + index}
                                                     style={{
                                                         marginTop: "-10px",
                                                         color: color,
                                                         display: "block",
                                                         fontSize: "0.95em",
                                                         lineHeight: "1.5em",
                                                         whiteSpace: "pre",
                                                         paddingLeft: "20px",
                                                         paddingBottom: "10px",
                                                     }}>{addLinks(reason)}</span>
                                    }
                                );
                                return <div key={messagePart.key}
                                            style={Object.assign({paddingLeft: "5px",}, cellStyle)}>
                                    <details className={"because"}>
                                        <summary style={{
                                            color: "rgb(222, 147, 95)",
                                            fontSize: "19px",
                                            lineHeight: "25px",
                                            paddingLeft: "5px",
                                            paddingTop: "0px",
                                            marginTop: "-1px",
                                        }}><span>
                                            <svg className={"summaryClosed"} viewBox="0 0 15 15" fill="currentColor" style={{
                                                verticalAlign: "top",
                                                color: "rgb(178, 148, 187)",
                                                height: "1em",
                                                width: "1em",
                                                paddingLeft: "2px",
                                                paddingTop: "5px"
                                            }}>
                                                <path d="M0 14l6-6-6-6z"/>
                                            </svg>
                                            <svg className={"summaryOpen"} viewBox="0 0 15 15" fill="currentColor" style={{
                                                verticalAlign: "top",
                                                color: "rgb(129, 162, 190)",
                                                height: "1em",
                                                width: "1em",
                                                paddingLeft: "2px",
                                                paddingTop: "5px",
                                                paddingBottom: "15px",
                                            }}>
                                                <path d="M0 5l6 6 6-6z"/>
                                            </svg>
                                            <span className={"summaryClosed"}>...</span>
                                        </span>
                                        </summary>
                                        {line}
                                    </details>
                                </div>;
                            } else if (messagePart.json) {
                                return <JsonItem key={messagePart.key}
                                                 index={null}
                                                 collapsed="0"
                                                 display={"table-cell"}
                                                 textStyle={{
                                                     fontFamily: "monospace, Roboto, sans-serif",
                                                     color: "rgb(255, 255, 255)",
                                                     display: "table-cell",
                                                     paddingLeft: "5px",
                                                     paddingRight: "5px",
                                                     whiteSpace: "pre",
                                                     letterSpacing: "0.08em",
                                                 }}
                                                 enableClipboard={true}
                                                 jsonItem={typeof messagePart.value === "number" ? "" + messagePart.value : messagePart.value}/>;
                            } else {
                                return <div key={messagePart.key}
                                            style={{
                                                fontFamily: "Roboto, sans-serif",
                                                color: "rgb(255, 255, 255)",
                                                display: "table-cell",
                                                paddingLeft: "5px",
                                                paddingRight: "5px",
                                                whiteSpace: "pre",
                                                letterSpacing: "0.08em",
                                            }}>{addLinks(messagePart.value)}</div>;
                            }
                        }
                    } else {
                        return <span/>;
                    }
                }) : <div style={Object.assign({
                    fontSize: "19px",
                    lineHeight: "25px"
                }, cellStyle)}>
                    <svg className={"logGroupSummaryClosed"} viewBox="0 0 15 15" fill="currentColor" style={{
                        verticalAlign: "top",
                        color: "rgb(178, 148, 187)",
                        height: "1em",
                        width: "1em",
                        paddingLeft: "2px",
                        paddingTop: "5px",
                    }}>
                        <path d="M0 14l6-6-6-6z"/>
                    </svg>
                    <svg className={"logGroupSummaryOpen"} viewBox="0 0 15 15" fill="currentColor" style={{
                        verticalAlign: "top",
                        color: "rgb(129, 162, 190)",
                        height: "1em",
                        width: "1em",
                        paddingLeft: "2px",
                        paddingTop: "5px",
                    }}>
                        <path d="M0 5l6 6 6-6z"/>
                    </svg>
                    <span className={"logGroupSummaryClosed"}>...</span></div>}
            </div>;
        }

        if (group) {
            return (<details className={"logGroup"}>
                <summary style={{
                    color: "rgb(222, 147, 95)",
                    // backgroundColor: "rgb(43, 52, 62)"
                }}><LogMessage key={group.key + "_summary"}
                               logMessage={group.value}/></summary>
                <div style={{
                    borderStyle: "dashed",
                    borderColor: "rgb(43, 52, 62)",
                    marginLeft: "35px",
                    marginTop: "10px",
                    marginRight: "5px",
                    marginBottom: "10px",
                    display: "inline-block",
                    paddingLeft: "5px",
                    paddingRight: "5px",
                    paddingBottom: "5px",
                }}>{logMessage.map((item, index) => <LogMessage index={index}
                                                                indent={true}
                                                                key={item.key}
                                                                logMessage={item.value}/>)}</div>
            </details>);
        } else {
            return renderItem(logMessage);
        }
    }
};

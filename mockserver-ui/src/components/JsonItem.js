import React, {Component} from 'react';
import ReactJson from 'react-json-view';
import PropTypes from "prop-types";
import './jsonItem.css';

export default class JsonItem extends Component {
    static propTypes = {
        description: PropTypes.oneOfType([PropTypes.object.isRequired, PropTypes.string.isRequired]),
        jsonItem: PropTypes.oneOfType([PropTypes.string.isRequired, PropTypes.object.isRequired, PropTypes.array.isRequired]).isRequired,
    };

    render() {
        const {
            collapsed = 0,
            jsonItem = null,
            description = null,
            display = "inline-block",
            enableClipboard = true,
            textStyle = {}
        } = this.props;

        function addDescription(description, jsonDiv) {
            if (description.json) {
                return <div key={"description"}
                            style={{
                                display: "table-row",
                                maxWidth: "200px",
                                overflow: "hidden"
                            }}>
                    <JsonItem key={"description_first"}
                              index={null}
                              collapsed="0"
                              display={"inline-block"}
                              textStyle={{
                                  fontFamily: "monospace, Roboto, sans-serif",
                                  display: "table-cell",
                                  padding: "5px",
                              }}
                              enableClipboard={false}
                              jsonItem={description.first}/>
                    <JsonItem key={"description_object"}
                              index={null}
                              collapsed="0"
                              display={"table-cell"}
                              textStyle={{
                                  fontFamily: "monospace, Roboto, sans-serif",
                                  display: "table-cell",
                                  verticalAlign: "top",
                                  padding: "5px",
                              }}
                              enableClipboard={false}
                              jsonItem={description.object}/>
                    <JsonItem key={"description_second"}
                              index={null}
                              collapsed="0"
                              display={"inline-block"}
                              textStyle={{
                                  fontFamily: "monospace, Roboto, sans-serif",
                                  display: "table-cell",
                                  padding: "5px",
                              }}
                              enableClipboard={false}
                              jsonItem={description.second}/>
                    {jsonDiv}
                </div>;
            } else {
                return (<div key={"wrap_div"}>
                    <JsonItem key={"description"}
                              index={null}
                              collapsed="0"
                              display={"inline-block"}
                              textStyle={{
                                  fontFamily: "monospace, Roboto, sans-serif",
                                  display: "table-cell",
                                  padding: "5px",
                                  maxWidth: "1100px",
                                  overflow: "hidden",
                                  textOverflow: "ellipsis"
                              }}
                              enableClipboard={false}
                              jsonItem={description}/>
                    {jsonDiv}
                </div>);
            }
        }

        if (typeof jsonItem === "object" || Array.isArray(jsonItem)) {
            if (description) {
                return addDescription(description, <ReactJson src={jsonItem}
                                                              key={"json"}
                                                              style={{
                                                                  whiteSpace: "nowrap",
                                                                  paddingTop: "6px",
                                                                  top: "-2px",
                                                                  display: display,
                                                              }}
                                                              name={null}
                                                              theme={"tomorrow"}
                                                              iconStyle={"triangle"}
                                                              indentWidth={4}
                                                              collapsed={collapsed != null ? collapsed : 0}
                                                              shouldCollapse={() => {
                                                                  return false
                                                              }}
                                                              enableClipboard={enableClipboard}
                                                              displayObjectSize={false}
                                                              displayDataTypes={false}
                                                              onEdit={false}
                                                              onAdd={false}
                                                              onDelete={false}/>);
            } else {
                return (<ReactJson src={jsonItem}
                                   key={"json"}
                                   style={{
                                       whiteSpace: "nowrap",
                                       paddingLeft: "5px",
                                       top: "-1px",
                                       display: display
                                   }}
                                   name={null}
                                   theme={"tomorrow"}
                                   iconStyle={"triangle"}
                                   indentWidth={4}
                                   collapsed={collapsed != null ? collapsed : 0}
                                   shouldCollapse={() => {
                                       return false
                                   }}
                                   enableClipboard={enableClipboard}
                                   displayObjectSize={false}
                                   displayDataTypes={false}
                                   onEdit={false}
                                   onAdd={false}
                                   onDelete={false}/>);
            }
        } else if (typeof jsonItem === "string") {
            return (<pre style={textStyle}>{jsonItem}</pre>);
        } else {
            return (<div/>);
        }
    }
};

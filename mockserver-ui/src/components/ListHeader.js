import React, {Component} from 'react';
import PropTypes from 'prop-types';

export default class ListHeader extends Component {
    static propTypes = {
        text: PropTypes.string.isRequired
    };

    render() {
        const {
            text = ""
        } = this.props;
        return (
            <div style={{
                font: "1.25em 'Averia Sans Libre', Helvetica, Arial, sans-serif",
                margin: "0px 0px 3px 2px"
            }}>{text}</div>
        )
    }
};

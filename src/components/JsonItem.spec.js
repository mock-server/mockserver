import React from 'react';
import {expect} from 'chai';
import Enzyme, {shallow} from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';
import JsonItem from './JsonItem';

Enzyme.configure({adapter: new Adapter()});

describe('<JsonItem />', () => {
    it('renders Form component', () => {
        const jsonItem = {
            key: "",
            value: {
                httpRequest: {
                    path: "somePath"
                },
                httpResponse: {
                    body: "someBody"
                }
            }
        };
        const wrapper = shallow(<JsonItem jsonItem={jsonItem}/>).dive();
        expect(wrapper.find('t')).to.have.length(3);
    });

    // it('simulates click events', () => {
    //     const onButtonClick = sinon.spy();
    //     const wrapper = shallow(<Foo onButtonClick={onButtonClick} />);
    //     wrapper.find('button').simulate('click');
    //     expect(onButtonClick).to.have.property('callCount', 1);
    // });
});

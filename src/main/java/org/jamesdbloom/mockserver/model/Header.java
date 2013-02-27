package org.jamesdbloom.mockserver.model;

import org.jamesdbloom.mockserver.client.serialization.model.HeaderDTO;

/**
 * @author jamesdbloom
 */
public class Header extends KeyToMultiValue<String, String> {

    public Header(String name, String... value) {
        super(name, value);
    }

    public Header(HeaderDTO header) {
        super(header.getName(), header.getValues());
    }
}

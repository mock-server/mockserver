package org.jamesdbloom.mockserver.model;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class Header extends KeyToMultiValue<String, String> {

    public Header(String name, String... value) {
        super(name, value);
    }

    public Header(String name, List<String> value) {
        super(name, value);
    }
}

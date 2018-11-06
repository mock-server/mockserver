package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Header;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HeaderDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        HeaderDTO header = new HeaderDTO(new Header("first", "first_one", "first_two"));

        // then
        assertThat(header.getValues(), containsInAnyOrder(string("first_one"), string("first_two")));
        assertThat(header.buildObject().getValues(), containsInAnyOrder(string("first_one"), string("first_two")));
    }
}

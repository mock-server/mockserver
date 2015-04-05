package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.Header;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * @author jamesdbloom
 */
public class HeaderDTOTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // when
        HeaderDTO header = new HeaderDTO(new Header("first", "first_one", "first_two"));

        // then
        assertThat(header.getValues(), containsInAnyOrder("first_one", "first_two"));
        assertThat(header.buildObject().getValues(), containsInAnyOrder("first_one", "first_two"));
    }
}

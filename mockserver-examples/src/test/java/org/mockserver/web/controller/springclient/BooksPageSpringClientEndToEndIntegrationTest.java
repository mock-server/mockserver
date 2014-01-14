package org.mockserver.web.controller.springclient;

import org.junit.runner.RunWith;
import org.mockserver.configuration.RootConfiguration;
import org.mockserver.servicebackend.BackEndServiceConfiguration;
import org.mockserver.web.configuration.WebMvcConfiguration;
import org.mockserver.web.controller.BooksPageEndToEndIntegrationTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * @author jamesdbloom
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({
        @ContextConfiguration(
                classes = {
                        RootConfiguration.class,
                        BackEndServiceConfiguration.class
                }
        ),
        @ContextConfiguration(
                classes = {
                        WebMvcConfiguration.class
                }
        )
})
@ActiveProfiles(profiles = {"backend", "springClient"})
public class BooksPageSpringClientEndToEndIntegrationTest extends BooksPageEndToEndIntegrationTest {

}

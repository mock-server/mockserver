package org.mockserver.web.controller.apacheclient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockserver.client.proxy.Times;
import org.mockserver.configuration.RootConfiguration;
import org.mockserver.integration.ClientAndProxy;
import org.mockserver.model.Parameter;
import org.mockserver.servicebackend.BackEndServiceConfiguration;
import org.mockserver.servicebackend.BookServer;
import org.mockserver.web.configuration.WebMvcConfiguration;
import org.mockserver.web.controller.BooksPageEndToEndIntegrationTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Resource;

import static org.mockserver.integration.ClientAndProxy.startClientAndProxy;
import static org.mockserver.model.HttpRequest.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

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
@ActiveProfiles(profiles = {"backend", "apacheClient"})
public class BooksPageApacheClientEndToEndIntegrationTest extends BooksPageEndToEndIntegrationTest {

}

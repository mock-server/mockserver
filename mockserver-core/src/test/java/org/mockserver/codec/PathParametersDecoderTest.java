package org.mockserver.codec;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.mockserver.model.Parameter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;

/**
 * See: https://swagger.io/docs/specification/serialization/
 */
public class PathParametersDecoderTest {

    @Test
    public void shouldValidWithNoPathParameters() {
        shouldValidPath(
            "/users/{id}",
            new Parameter[]{
            },
            ""
        );
        shouldValidPath(
            "/users/{id}/{name}",
            new Parameter[]{
            },
            ""
        );
        shouldValidPath(
            "someP[a-z]{2}",
            new Parameter[]{
            },
            ""
        );
        shouldValidPath(
            "/users/{id}/{name}/",
            new Parameter[]{
            },
            ""
        );
    }

    @Test
    public void shouldValidPathMatcherForPathParametersForStyleSimpleAndExplodedFalse() {
        shouldValidPath(
            "/users/{id}",
            new Parameter[]{
                param("id", ".*")
            },
            ""
        );
        shouldValidPath(
            "/users/{id}/{name}",
            new Parameter[]{
                param("id", ".*")
            },
            "path parameters specified [id] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{id}/{name}",
            new Parameter[]{
                param("name", ".*")
            },
            "path parameters specified [name] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{id}/{name}",
            new Parameter[]{
                param("id", ".*"),
                param("name", ".*")
            },
            ""
        );
    }

    @Test
    public void shouldValidPathMatcherForPathParametersForStyleSimpleAndExplodedFalseWithTrailingSlash() {
        shouldValidPath(
            "/users/{id}/",
            new Parameter[]{
                param("id", ".*")
            },
            ""
        );
        shouldValidPath(
            "/users/{id}/{name}/",
            new Parameter[]{
                param("id", ".*")
            },
            "path parameters specified [id] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{id}/{name}/",
            new Parameter[]{
                param("name", ".*")
            },
            "path parameters specified [name] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{id}/{name}/",
            new Parameter[]{
                param("id", ".*"),
                param("name", ".*")
            },
            ""
        );
    }

    @Test
    public void shouldValidPathMatcherForPathParametersForStyleSimpleAndExplodedTrue() {
        shouldValidPath(
            "/users/{id*}",
            new Parameter[]{
                param("id", ".*")
            },
            ""
        );
        shouldValidPath(
            "/users/{id*}/{name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "path parameters specified [id] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{id*}/{name*}",
            new Parameter[]{
                param("name", ".*")
            },
            "path parameters specified [name] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{id*}/{name*}",
            new Parameter[]{
                param("id", ".*"),
                param("name", ".*")
            },
            ""
        );
    }

    @Test
    public void shouldValidPathMatcherForPathParametersForStyleLabelAndExplodedFalse() {
        shouldValidPath(
            "/users/{.id}",
            new Parameter[]{
                param("id", ".*")
            },
            ""
        );
        shouldValidPath(
            "/users/{.id}/{.name}",
            new Parameter[]{
                param("id", ".*")
            },
            "path parameters specified [id] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{.id}/{.name}",
            new Parameter[]{
                param("name", ".*")
            },
            "path parameters specified [name] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{.id}/{.name}",
            new Parameter[]{
                param("id", ".*"),
                param("name", ".*")
            },
            ""
        );
    }

    @Test
    public void shouldValidPathMatcherForPathParametersForStyleLabelAndExplodedTrue() {
        shouldValidPath(
            "/users/{.id*}",
            new Parameter[]{
                param("id", ".*")
            },
            ""
        );
        shouldValidPath(
            "/users/{.id*}/{.name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "path parameters specified [id] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{.id*}/{.name*}",
            new Parameter[]{
                param("name", ".*")
            },
            "path parameters specified [name] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{.id*}/{.name*}",
            new Parameter[]{
                param("id", ".*"),
                param("name", ".*")
            },
            ""
        );
    }

    @Test
    public void shouldValidPathMatcherForPathParametersForStyleMatrixAndExplodedFalse() {
        shouldValidPath(
            "/users/{;id}",
            new Parameter[]{
                param("id", ".*")
            },
            ""
        );
        shouldValidPath(
            "/users/{;id}/{;name}",
            new Parameter[]{
                param("id", ".*")
            },
            "path parameters specified [id] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{;id}/{;name}",
            new Parameter[]{
                param("name", ".*")
            },
            "path parameters specified [name] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{;id}/{;name}",
            new Parameter[]{
                param("id", ".*"),
                param("name", ".*")
            },
            ""
        );
    }

    @Test
    public void shouldValidPathMatcherForPathParametersForStyleMatrixAndExplodedTrue() {
        shouldValidPath(
            "/users/{;id*}",
            new Parameter[]{
                param("id", ".*")
            },
            ""
        );
        shouldValidPath(
            "/users/{;id*}/{;name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "path parameters specified [id] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{;id*}/{;name*}",
            new Parameter[]{
                param("name", ".*")
            },
            "path parameters specified [name] but found [id, name] in path matcher"
        );
        shouldValidPath(
            "/users/{;id*}/{;name*}",
            new Parameter[]{
                param("id", ".*"),
                param("name", ".*")
            },
            ""
        );
    }

    private void shouldValidPath(String path, Parameter[] pathParameters, String error) {
        assertThat(new PathParametersDecoder().validatePath(
            request()
                .withPath(
                    path
                )
                .withPathParameters(
                    pathParameters
                )
        ), is(error));
    }

    @Test
    public void shouldNormaliseWithNoPathParameters() {
        shouldNormalisePath(
            "/users/{id}",
            new Parameter[]{},
            "/users/{id}"
        );
        shouldNormalisePath(
            "someP[a-z]{2}",
            new Parameter[]{},
            "someP[a-z]{2}"
        );
    }

    @Test
    public void shouldNormalisePathParametersForStyleSimpleAndExplodedFalse() {
        shouldNormalisePath(
            "/users/{id}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*"
        );
        shouldNormalisePath(
            "/users/{id}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*"
        );
        shouldNormalisePath(
            "/users/{id}/{name}",
            new Parameter[]{
                param("id", ".*"),
                param("name", ".*")
            },
            "/users/.*/.*"
        );
        shouldNormalisePath(
            "/users/{id}/{name}",
            new Parameter[]{
                param("id", ".*"),
                param("name", ".*")
            },
            "/users/.*/.*"
        );
    }

    @Test
    public void shouldNormalisePathParametersWithTrailingSlashForStyleSimpleAndExplodedFalse() {
        shouldNormalisePath(
            "/users/{id}/",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/"
        );
        shouldNormalisePath(
            "/users/{id}/",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/"
        );
        shouldNormalisePath(
            "/users/{id}/{name}/",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/.*/"
        );
        shouldNormalisePath(
            "/users/{id}/{name}/",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/.*/"
        );
    }

    @Test
    public void shouldNormalisePathParametersForStyleSimpleAndExplodedTrue() {
        shouldNormalisePath(
            "/users/{id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*"
        );
        shouldNormalisePath(
            "/users/{id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*"
        );
        shouldNormalisePath(
            "/users/{id*}/{name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/.*"
        );
        shouldNormalisePath(
            "/users/{id*}/{name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/.*"
        );
    }

    @Test
    public void shouldNormalisePathParametersForStyleLabelAndExplodedFalse() {
        shouldNormalisePath(
            "/users/{.id}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*"
        );
        shouldNormalisePath(
            "/users/{.id}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*"
        );
        shouldNormalisePath(
            "/users/{.id}/{.name}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/.*"
        );
        shouldNormalisePath(
            "/users/{.id}/{.name}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/.*"
        );
    }

    @Test
    public void shouldNormalisePathParametersForStyleLabelAndExplodedTrue() {
        shouldNormalisePath(
            "/users/{.id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*"
        );
        shouldNormalisePath(
            "/users/{.id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*"
        );
        shouldNormalisePath(
            "/users/{.id*}/{.name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/.*"
        );
        shouldNormalisePath(
            "/users/{.id*}/{.name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/.*"
        );
    }

    @Test
    public void shouldNormalisePathParametersForStyleMatrixAndExplodedFalse() {
        shouldNormalisePath(
            "/users/{;id}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*"
        );
        shouldNormalisePath(
            "/users/{;id}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*"
        );
        shouldNormalisePath(
            "/users/{;id}/{;name}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/.*"
        );
        shouldNormalisePath(
            "/users/{;id}/{;name}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/.*"
        );
    }

    @Test
    public void shouldNormalisePathParametersForStyleMatrixAndExplodedTrue() {
        shouldNormalisePath(
            "/users/{;id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*"
        );
        shouldNormalisePath(
            "/users/{;id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*"
        );
        shouldNormalisePath(
            "/users/{;id*}/{;name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/.*"
        );
        shouldNormalisePath(
            "/users/{;id*}/{;name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.*/.*"
        );
    }

    void shouldNormalisePath(String matcherPath, Parameter[] parameter, String expected) {
        assertThat(new PathParametersDecoder().normalisePathWithParametersForMatching(
            request()
                .withPath(
                    matcherPath
                )
                .withPathParameters(
                    parameter
                )
        ), is(string(expected)));
    }

    @Test
    public void shouldRetrievePathParametersWithNoPathParameters() {
        shouldRetrieveParameters(
            "/users/{id}",
            new Parameter[]{
            },
            "/users/5",
            ImmutableList.of(
            )
        );
        shouldRetrieveParameters(
            "/users/{id}",
            new Parameter[]{
            },
            "/users/3,4,5",
            ImmutableList.of(
            )
        );
        shouldRetrieveParameters(
            "/users/{id}/{name}",
            new Parameter[]{
            },
            "/users/5/bob",
            ImmutableList.of(
            )
        );
        shouldRetrieveParameters(
            "/users/{id}/{name}",
            new Parameter[]{
            },
            "/users/3,4,5/bob,bill,tony",
            ImmutableList.of(
            )
        );
    }

    @Test
    public void shouldRetrievePathParametersForStyleSimpleAndExplodedFalse() {
        shouldRetrieveParameters(
            "/users/{id}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/5",
            Collections.singletonList(
                param("id", "5")
            )
        );
        shouldRetrieveParameters(
            "/users/{id}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/3,4,5",
            Collections.singletonList(
                param("id", "3", "4", "5")
            )
        );
        shouldRetrieveParameters(
            "/users/{id}/{name}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/5/bob",
            Arrays.asList(
                param("id", "5"),
                param("name", "bob")
            )
        );
        shouldRetrieveParameters(
            "/users/{id}/{name}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/3,4,5/bob,bill,tony",
            Arrays.asList(
                param("id", "3", "4", "5"),
                param("name", "bob", "bill", "tony")
            )
        );
    }

    @Test
    public void shouldRetrievePathParametersForStyleSimpleAndExplodedTrue() {
        shouldRetrieveParameters(
            "/users/{id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/5",
            Collections.singletonList(
                param("id", "5")
            )
        );
        shouldRetrieveParameters(
            "/users/{id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/3,4,5",
            Collections.singletonList(
                param("id", "3", "4", "5")
            )
        );
        shouldRetrieveParameters(
            "/users/{id*}/{name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/5/bob",
            Arrays.asList(
                param("id", "5"),
                param("name", "bob")
            )
        );
        shouldRetrieveParameters(
            "/users/{id*}/{name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/3,4,5/bob,bill,tony",
            Arrays.asList(
                param("id", "3", "4", "5"),
                param("name", "bob", "bill", "tony")
            )
        );
    }

    @Test
    public void shouldRetrievePathParametersForStyleLabelAndExplodedFalse() {
        shouldRetrieveParameters(
            "/users/{.id}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.5",
            Collections.singletonList(
                param("id", "5")
            )
        );
        shouldRetrieveParameters(
            "/users/{.id}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.3,4,5",
            Collections.singletonList(
                param("id", "3", "4", "5")
            )
        );
        shouldRetrieveParameters(
            "/users/{.id}/{.name}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.5/.bob",
            Arrays.asList(
                param("id", "5"),
                param("name", "bob")
            )
        );
        shouldRetrieveParameters(
            "/users/{.id}/{.name}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.3,4,5/.bob,bill,tony",
            Arrays.asList(
                param("id", "3", "4", "5"),
                param("name", "bob", "bill", "tony")
            )
        );
    }

    @Test
    public void shouldRetrievePathParametersForStyleLabelAndExplodedTrue() {
        shouldRetrieveParameters(
            "/users/{.id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.5",
            Collections.singletonList(
                param("id", "5")
            )
        );
        shouldRetrieveParameters(
            "/users/{.id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.3.4.5",
            Collections.singletonList(
                param("id", "3.4.5")
            )
        );
        shouldRetrieveParameters(
            "/users/{.id*}/{.name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.5/.bob",
            Arrays.asList(
                param("id", "5"),
                param("name", "bob")
            )
        );
        shouldRetrieveParameters(
            "/users/{.id*}/{.name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/.3.4.5/.bob.bill.tony",
            Arrays.asList(
                param("id", "3.4.5"),
                param("name", "bob.bill.tony")
            )
        );
    }

    @Test
    public void shouldRetrievePathParametersForStyleMatrixAndExplodedFalse() {
        shouldRetrieveParameters(
            "/users/{;id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/;id=5",
            Collections.singletonList(
                param("id", "5")
            )
        );
        shouldRetrieveParameters(
            "/users/{;id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/;id=3,4,5",
            Collections.singletonList(
                param("id", "3", "4", "5")
            )
        );
        shouldRetrieveParameters(
            "/users/{;id}/{;name}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/;id=5/;name=bob",
            Arrays.asList(
                param("id", "5"),
                param("name", "bob")
            )
        );
        shouldRetrieveParameters(
            "/users/{;id}/{;name}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/;id=3,4,5/;name=bob,bill,tony",
            Arrays.asList(
                param("id", "3", "4", "5"),
                param("name", "bob", "bill", "tony")
            )
        );
    }

    @Test
    public void shouldRetrievePathParametersForStyleMatrixAndExplodedTrue() {
        shouldRetrieveParameters(
            "/users/{;id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/;id=5",
            Collections.singletonList(
                param("id", "5")
            )
        );
        shouldRetrieveParameters(
            "/users/{;id*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/;id=3;id=4;id=5",
            Collections.singletonList(
                param("id", "3;id=4;id=5")
            )
        );
        shouldRetrieveParameters(
            "/users/{;id*}/{;name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/;id=5/;name=bob",
            Arrays.asList(
                param("id", "5"),
                param("name", "bob")
            )
        );
        shouldRetrieveParameters(
            "/users/{;id*}/{;name*}",
            new Parameter[]{
                param("id", ".*")
            },
            "/users/;id=3;id=4;id=5/;name=bob;name=bill;name=tony",
            Arrays.asList(
                param("id", "3;id=4;id=5"),
                param("name", "bob;name=bill;name=tony")
            )
        );
    }

    void shouldRetrieveParameters(String matcherPath, Parameter[] parameter, String requestPath, List<Parameter> expected) {
        assertThat(new PathParametersDecoder().extractPathParameters(
            request()
                .withPath(
                    matcherPath
                )
                .withPathParameters(
                    parameter
                ),
            request()
                .withPath(
                    requestPath
                )
        ).getEntries(), containsInAnyOrder(expected.toArray()));
    }

}
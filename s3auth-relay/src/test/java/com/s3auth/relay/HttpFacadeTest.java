/**
 * Copyright (c) 2012, s3auth.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the s3auth.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.s3auth.relay;

import com.jcabi.aspects.Parallel;
import com.jcabi.aspects.Tv;
import com.rexsl.test.Response;
import com.rexsl.test.request.JdkRequest;
import com.rexsl.test.response.RestResponse;
import com.s3auth.hosts.Host;
import com.s3auth.hosts.Hosts;
import com.s3auth.hosts.Range;
import com.s3auth.hosts.Resource;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.client.utils.DateUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Test case for {@link HttpFacade}.
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @checkstyle MultipleStringLiteralsCheck (500 lines)
 * @checkstyle MagicNumberCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class HttpFacadeTest {

    /**
     * HttpFacade can process parallel requests.
     * @throws Exception If there is some problem inside
     */
    @Test
    public void handlesParallelHttpRequests() throws Exception {
        final Host host = Mockito.mock(Host.class);
        Mockito.doAnswer(
            new Answer<Resource>() {
                @Override
                public Resource answer(final InvocationOnMock inv)
                    throws InterruptedException {
                    TimeUnit.SECONDS.sleep(1L);
                    throw new IllegalStateException("hello, world!");
                }
            }
        ).when(host).fetch(Mockito.any(URI.class), Mockito.any(Range.class));
        final Hosts hosts = Mockito.mock(Hosts.class);
        Mockito.doReturn(host).when(hosts).find(Mockito.anyString());
        final int port = PortMocker.reserve();
        final HttpFacade facade = new HttpFacade(hosts, port);
        facade.listen();
        final URI uri = UriBuilder
            .fromUri(String.format("http://localhost:%d/", port))
            .path("/a").build();
        try {
            HttpFacadeTest.http(uri);
        } finally {
            facade.close();
        }
    }

    /**
     * HttpFacade can process the If-Modified-Since header.
     * @throws Exception If there is some problem inside
     */
    @Test
    public void handlesIfModifiedSinceHeader() throws Exception {
        final Host host = Mockito.mock(Host.class);
        Mockito.doAnswer(
            new Answer<Resource>() {
                @Override
                public Resource answer(final InvocationOnMock inv)
                    throws InterruptedException {
                    final Resource answer = Mockito.mock(Resource.class);
                    Mockito.doReturn(new Date(5000L))
                        .when(answer).lastModified();
                    Mockito.doReturn(HttpURLConnection.HTTP_OK)
                        .when(answer).status();
                    return answer;
                }
            }
        ).when(host).fetch(Mockito.any(URI.class), Mockito.any(Range.class));
        final Hosts hosts = Mockito.mock(Hosts.class);
        Mockito.doReturn(host).when(hosts).find(Mockito.anyString());
        final int port = PortMocker.reserve();
        final HttpFacade facade = new HttpFacade(hosts, port);
        facade.listen();
        final URI uri = UriBuilder
            .fromUri(String.format("http://localhost:%d/", port))
            .path("/a").build();
        try {
            new JdkRequest(uri)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    String.format(
                        "Basic %s",
                        Base64.encodeBase64String("a:b".getBytes())
                    )
                )
                .header(
                    HttpHeaders.IF_MODIFIED_SINCE,
                    DateUtils.formatDate(new Date(2000L))
                ).uri().back().fetch().as(RestResponse.class)
                .assertStatus(HttpURLConnection.HTTP_OK);
            new JdkRequest(uri)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    String.format(
                        "Basic %s",
                        Base64.encodeBase64String("a:b".getBytes())
                    )
                )
                .header(
                    HttpHeaders.IF_MODIFIED_SINCE,
                    DateUtils.formatDate(new Date(10000L))
                ).uri().back().fetch().as(RestResponse.class)
                .assertStatus(HttpURLConnection.HTTP_NOT_MODIFIED);
        } finally {
            facade.close();
        }
    }

    /**
     * HttpFacade returns the Last-Modified header with the response.
     * @throws Exception If there is some problem inside
     */
    @Test
    public void respondsWithLastModifiedHeader() throws Exception {
        final Date date = new Date();
        final Host host = Mockito.mock(Host.class);
        Mockito.doAnswer(
            new Answer<Resource>() {
                @Override
                public Resource answer(final InvocationOnMock inv)
                    throws InterruptedException {
                    final Resource answer = Mockito.mock(Resource.class);
                    Mockito.doReturn(date)
                        .when(answer).lastModified();
                    Mockito.doReturn(HttpURLConnection.HTTP_OK)
                        .when(answer).status();
                    return answer;
                }
            }
        ).when(host).fetch(Mockito.any(URI.class), Mockito.any(Range.class));
        final Hosts hosts = Mockito.mock(Hosts.class);
        Mockito.doReturn(host).when(hosts).find(Mockito.anyString());
        final int port = PortMocker.reserve();
        final HttpFacade facade = new HttpFacade(hosts, port);
        try {
            facade.listen();
            final URI uri = UriBuilder
                .fromUri(String.format("http://localhost:%d/", port))
                .path("/a").build();
            final Response resp = new JdkRequest(uri)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    String.format(
                        "Basic %s",
                        Base64.encodeBase64String("a:b".getBytes())
                    )
                ).uri().back().fetch().as(RestResponse.class)
                .assertStatus(HttpURLConnection.HTTP_OK);
            MatcherAssert.assertThat(
                resp.headers().get(HttpHeaders.LAST_MODIFIED).get(0),
                Matchers.is(DateUtils.formatDate(date))
            );
        } finally {
            facade.close();
        }
    }

    /**
     * HttpFacade returns the Age header with the response.
     * @throws Exception If there is some problem inside
     */
    @Test
    public void respondsWithAgeHeader() throws Exception {
        final Host host = Mockito.mock(Host.class);
        Mockito.doAnswer(
            new Answer<Resource>() {
                @Override
                public Resource answer(final InvocationOnMock inv)
                    throws InterruptedException {
                    final Resource answer = Mockito.mock(Resource.class);
                    Mockito.doReturn(HttpURLConnection.HTTP_OK)
                        .when(answer).status();
                    Thread.sleep(1100L);
                    return answer;
                }
            }
        ).when(host).fetch(Mockito.any(URI.class), Mockito.any(Range.class));
        final Hosts hosts = Mockito.mock(Hosts.class);
        Mockito.doReturn(host).when(hosts).find(Mockito.anyString());
        final int port = PortMocker.reserve();
        final HttpFacade facade = new HttpFacade(hosts, port);
        try {
            facade.listen();
            final URI uri = UriBuilder
                .fromUri(String.format("http://localhost:%d/", port))
                .path("/a").build();
            final Response resp = new JdkRequest(uri)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    String.format(
                        "Basic %s",
                        Base64.encodeBase64String("a:b".getBytes())
                    )
                ).uri().back().fetch().as(RestResponse.class)
                .assertStatus(HttpURLConnection.HTTP_OK);
            MatcherAssert.assertThat(
                Integer.parseInt(resp.headers().get("Age").get(0)),
                Matchers.greaterThanOrEqualTo(1)
            );
        } finally {
            facade.close();
        }
    }

    /**
     * Make HTTP request.
     * @param path URI to hit
     * @throws Exception If fails
     */
    @Parallel(threads = Tv.FIFTY)
    private static void http(final URI path) throws Exception {
        new JdkRequest(path)
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
            .header(
                HttpHeaders.AUTHORIZATION,
                String.format(
                    "Basic %s",
                    Base64.encodeBase64String("a:b".getBytes())
                )
            )
            .uri()
            .queryParam("rnd", RandomStringUtils.randomAlphabetic(Tv.FIVE))
            .back()
            .fetch().as(RestResponse.class)
            .assertStatus(HttpURLConnection.HTTP_INTERNAL_ERROR)
            .assertBody(Matchers.containsString("hello"));
    }

}

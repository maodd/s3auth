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
package com.s3auth.hosts;

import java.net.URI;
import org.mockito.Mockito;

/**
 * Mocker of {@link Host}.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 0.0.1
 */
public final class HostMocker {

    /**
     * The mock.
     */
    private final transient Host host = Mockito.mock(Host.class);

    /**
     * Public ctor.
     */
    public HostMocker() {
        try {
            Mockito.doReturn(new ResourceMocker().withContent("hello").mock())
                .when(this.host)
                .fetch(Mockito.any(URI.class), Mockito.any(Range.class));
            Mockito.doReturn(true).when(this.host).authorized(
                Mockito.anyString(),
                Mockito.anyString()
            );
            Mockito.doReturn(true).when(this.host)
                .isHidden(Mockito.any(URI.class));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * With this content for this URI.
     * @param uri The URI to match
     * @param content The content to return
     * @return This object
     */
    public HostMocker withContent(final URI uri, final String content) {
        try {
            Mockito.doReturn(new ResourceMocker().withContent(content).mock())
                .when(this.host)
                .fetch(Mockito.eq(uri), Mockito.any(Range.class));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        return this;
    }

    /**
     * Mock it.
     * @return The host
     */
    public Host mock() {
        return this.host;
    }

}

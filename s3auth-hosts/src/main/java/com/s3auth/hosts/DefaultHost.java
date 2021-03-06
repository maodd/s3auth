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

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.BucketWebsiteConfiguration;
import com.jcabi.aspects.Immutable;
import com.jcabi.aspects.Loggable;
import com.jcabi.log.Logger;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

/**
 * Default implementation of {@link Host}.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 0.0.1
 */
@Immutable
@EqualsAndHashCode(of = "bucket")
@Loggable(Loggable.DEBUG)
final class DefaultHost implements Host {

    /**
     * The S3 bucket.
     */
    private final transient Bucket bucket;

    /**
     * Htpasswd file abstraction.
     */
    private final transient Htpasswd htpasswd;

    /**
     * Public ctor.
     * @param bckt The S3 bucket to use
     */
    DefaultHost(@NotNull final Bucket bckt) {
        this.bucket = bckt;
        this.htpasswd = new Htpasswd(this);
    }

    @Override
    public String toString() {
        return this.bucket.toString();
    }

    @Override
    public void close() throws IOException {
        // nothing to do
    }

    @Override
    @NotNull
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    @Loggable(value = Loggable.DEBUG, ignore = IOException.class)
    public Resource fetch(@NotNull final URI uri, @NotNull final Range range)
        throws IOException {
        Resource resource = null;
        final Collection<String> errors = new LinkedList<String>();
        for (final DefaultHost.ObjectName name : this.names(uri)) {
            try {
                resource = new DefaultResource(
                    this.bucket.client(), this.bucket.bucket(),
                    name.get(), range
                );
                break;
            } catch (final AmazonServiceException ex) {
                if ("NoSuchBucket".equals(ex.getErrorCode())) {
                    throw new IOException(
                        Logger.format(
                            "The bucket '%s' does not exist.",
                            this.bucket.bucket()
                        ),
                        ex
                    );
                }
                // @checkstyle MultipleStringLiterals (1 line)
                errors.add(String.format("'%s': %s", name, ex.getMessage()));
            } catch (final AmazonClientException ex) {
                errors.add(String.format("'%s': %s", name, ex.getMessage()));
            }
        }
        if (resource == null) {
            throw new IOException(
                Logger.format(
                    "failed to fetch %s from '%s' (key=%s): %[list]s",
                    uri, this.bucket.name(), this.bucket.key(), errors
                )
            );
        }
        return resource;
    }

    @Override
    public boolean isHidden(@NotNull final URI uri) {
        return true;
    }

    @Override
    public boolean authorized(@NotNull final String user,
        @NotNull final String password) throws IOException {
        final boolean auth;
        if (user.equals(this.bucket.key())
            && password.equals(this.bucket.secret())) {
            auth = true;
        } else {
            auth = this.htpasswd.authorized(user, password);
        }
        return auth;
    }

    /**
     * Convert URI to all possible S3 object names (in order of importance).
     * @param uri The URI
     * @return Object names
     */
    private Iterable<DefaultHost.ObjectName> names(final URI uri) {
        final String name = StringUtils.strip(uri.getPath(), "/");
        final Collection<DefaultHost.ObjectName> names =
            new LinkedList<DefaultHost.ObjectName>();
        if (!name.isEmpty()) {
            names.add(
                new DefaultHost.ObjectName() {
                    @Override
                    public String get() {
                        return name;
                    }
                    @Override
                    public String toString() {
                        return name;
                    }
                }
            );
        }
        names.add(new DefaultHost.NameWithSuffix(name));
        return names;
    }

    /**
     * Object name with a suffix from a bucket.
     */
    @Loggable(Loggable.DEBUG)
    private final class NameWithSuffix implements DefaultHost.ObjectName {
        /**
         * Original name.
         */
        private final transient String origin;
        /**
         * Public ctor.
         * @param name The original name
         */
        NameWithSuffix(final String name) {
            this.origin = name;
        }
        @Override
        public String get() {
            String suffix = null;
            try {
                final BucketWebsiteConfiguration conf =
                    DefaultHost.this.bucket.client()
                        .getBucketWebsiteConfiguration(
                            DefaultHost.this.bucket.name()
                        );
                if (conf != null) {
                    suffix = conf.getIndexDocumentSuffix();
                }
            } catch (final AmazonClientException ex) {
                suffix = "";
            }
            if (suffix == null || suffix.isEmpty()) {
                suffix = "index.html";
            }
            final StringBuilder text = new StringBuilder(this.origin);
            if (text.length() > 0) {
                text.append('/');
            }
            text.append(suffix);
            return text.toString();
        }
        @Override
        public String toString() {
            return String.format("%s+suffix", this.origin);
        }
    }

    /**
     * Name of an S3 Object, context dependent.
     */
    private interface ObjectName {
        /**
         * Returns a name of S3 object.
         * @return The name
         */
        String get();
    }

}

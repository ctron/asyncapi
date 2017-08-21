/*
 * Copyright (C) 2017 Jens Reimann <jreimann@redhat.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dentrassi.asyncapi.meta;

import java.net.URI;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class Information {
    private String title;
    private String version;
    private String description;
    private URI termsOfService;
    private License license;

    public String getTitle() {
        return this.title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getVersion() {
        return this.version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public URI getTermsOfService() {
        return this.termsOfService;
    }

    public void setTermsOfService(final URI termsOfService) {
        this.termsOfService = termsOfService;
    }

    public License getLicense() {
        return this.license;
    }

    public void setLicense(final License license) {
        this.license = license;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("title", this.title)
                .append("version", this.version)
                .append("description", this.description)
                .append("termsOfService", this.termsOfService)
                .append("license", this.license)
                .toString();
    }
}

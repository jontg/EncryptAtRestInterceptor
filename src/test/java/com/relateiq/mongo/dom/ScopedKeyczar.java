package com.relateiq.mongo.dom;

import org.mongodb.morphia.annotations.Id;

import java.util.Map;

/**
 * Created by jgretarsson on 11/28/14.
 */
public class ScopedKeyczar {
    @Id private String scope;
    private String metadata;
    private Map<String, String> secrets;

    public ScopedKeyczar() {
    }

    public ScopedKeyczar(String scope, String metadata, Map<String, String> secrets) {
        this.scope = scope;
        this.metadata = metadata;
        this.secrets = secrets;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Map<String, String> getSecrets() {
        return secrets;
    }

    public void setSecrets(Map<String, String> secrets) {
        this.secrets = secrets;
    }

    public static ScopedKeyczarBuilder newBuilder() {
        return new ScopedKeyczarBuilder();
    }

    public static class ScopedKeyczarBuilder {
        private String scope;
        private String metadata;
        private Map<String, String> secrets;

        public ScopedKeyczarBuilder setSecrets(Map<String, String> secrets) {
            this.secrets = secrets;
            return this;
        }

        public ScopedKeyczarBuilder setMetadata(String metadata) {
            this.metadata = metadata;
            return this;
        }

        public ScopedKeyczarBuilder setScope(String scope) {
            this.scope = scope;
            return this;
        }

        public ScopedKeyczar build() {
            return new ScopedKeyczar(scope, metadata, secrets);
        }
    }
}

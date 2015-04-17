package org.jenkinsci.plugins.docker.commons.impl;

import hudson.EnvVars;
import org.jenkinsci.plugins.docker.commons.KeyMaterial;
import org.jenkinsci.plugins.docker.commons.KeyMaterialFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.io.Serializable;

/**
 * Composes multiple {@link org.jenkinsci.plugins.docker.commons.KeyMaterialFactory}s into one.
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
public class CompositeKeyMaterialFactory extends KeyMaterialFactory {
    private final KeyMaterialFactory[] factories;

    public CompositeKeyMaterialFactory(KeyMaterialFactory... factories) {
        this.factories = factories == null || factories.length == 0
                ? new KeyMaterialFactory[]{new NullKeyMaterialFactory()}
                : factories.clone();
    }

    @Override
    public KeyMaterial materialize() throws IOException, InterruptedException {

        KeyMaterial[] keyMaterials = new KeyMaterial[factories.length];
        EnvVars env = new EnvVars();
        try {
            for (int index = 0; index < factories.length; index++) {
                keyMaterials[index] = factories[index].materialize();
                env.putAll(keyMaterials[index].env());
            }
            return new CompositeKeyMaterial(env, keyMaterials);
        } catch (Exception e) {
            for (int index = keyMaterials.length - 1; index >= 0; index--) {
                try {
                    if (keyMaterials[index] != null) {
                        keyMaterials[index].close();
                    }
                } catch (IOException ioe) {
                    // ignore
                }
            }
            // TODO Java 7+ use chained exceptions
            if (e instanceof IOException) {
                throw (IOException) e;
            } else if (e instanceof InterruptedException) {
                throw (InterruptedException) e;
            } else {
                throw new IOException("Error materializing credentials.", e);
            }
        }
    }

    private static final class CompositeKeyMaterial extends KeyMaterial implements Serializable {

        private static final long serialVersionUID = 1L;

        private final KeyMaterial[] keyMaterials;

        protected CompositeKeyMaterial(EnvVars envVars, KeyMaterial... keyMaterials) {
            super(envVars);
            this.keyMaterials = keyMaterials;
        }

        @Override
        public void close() throws IOException {
            IOException first = null;
            for (int index = keyMaterials.length - 1; index >= 0; index--) {
                try {
                    if (keyMaterials[index] != null) {
                        keyMaterials[index].close();
                    }
                } catch (IOException ioe) {
                    first = first == null ? ioe : first;
                }
            }
            if (first != null) {
                throw first;
            }
        }
    }
}

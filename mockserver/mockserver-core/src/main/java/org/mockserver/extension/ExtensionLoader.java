package org.mockserver.extension;

import org.mockserver.configuration.Configuration;
import org.mockserver.mock.action.http.CallbackClassLoaderResolver;

import java.lang.reflect.InvocationTargetException;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Builds the extension instances one server loads: named classes first, then embedded classes and suppliers, then
 * (only when scanning is on) {@link ServiceLoader} entries. A class is loaded once however many sources name it.
 * Any problem throws {@link ExtensionException}; nothing has been started at that point.
 */
final class ExtensionLoader {

    private static final Pattern NAME = Pattern.compile("[a-z0-9-]+");

    private ExtensionLoader() {
    }

    static List<LoadedExtension> load(Configuration configuration) {
        ClassLoader classLoader = CallbackClassLoaderResolver.resolveClassLoader();
        Set<Class<?>> seen = new HashSet<>();
        List<Extension> instances = new ArrayList<>();

        String classNames = configuration.extensions();
        if (isNotBlank(classNames)) {
            for (String className : classNames.split(",")) {
                String trimmed = className.trim();
                if (!trimmed.isEmpty()) {
                    Class<?> type = loadClass(trimmed, classLoader);
                    if (seen.add(type)) {
                        instances.add(instantiate(type));
                    }
                }
            }
        }
        for (Class<? extends Extension> type : configuration.extensionClasses()) {
            if (type != null && seen.add(type)) {
                instances.add(instantiate(type));
            }
        }
        for (Supplier<? extends Extension> supplier : configuration.extensionSuppliers()) {
            Extension extension;
            try {
                extension = supplier != null ? supplier.get() : null;
            } catch (RuntimeException | LinkageError e) {
                throw new ExtensionException("an extension supplier threw: " + e, e);
            }
            if (extension == null) {
                throw new ExtensionException("an extension supplier returned null");
            }
            if (seen.add(extension.getClass())) {
                instances.add(extension);
            }
        }
        if (Boolean.TRUE.equals(configuration.extensionScanning())) {
            try {
                Iterator<ServiceLoader.Provider<Extension>> providers = ServiceLoader.load(Extension.class, classLoader).stream().iterator();
                while (providers.hasNext()) {
                    ServiceLoader.Provider<Extension> provider = providers.next();
                    if (seen.add(provider.type())) {
                        instances.add(provider.get());
                    }
                }
            } catch (ServiceConfigurationError error) {
                throw new ExtensionException("extension scanning failed: " + error.getMessage(), error);
            }
        }

        Map<String, LoadedExtension> byName = new LinkedHashMap<>();
        for (Extension extension : instances) {
            String name = nameOf(extension);
            LoadedExtension previous = byName.put(name, new LoadedExtension(extension, name, priorityOf(extension), sourceOf(extension.getClass())));
            if (previous != null) {
                throw new ExtensionException("extensions " + previous.className() + " and " + extension.getClass().getName() + " both have the name \"" + name + "\"; extension names must be unique");
            }
        }
        List<LoadedExtension> loaded = new ArrayList<>(byName.values());
        loaded.sort(Comparator.comparingInt(LoadedExtension::priority).thenComparing(LoadedExtension::name));
        return loaded;
    }

    private static Class<?> loadClass(String className, ClassLoader classLoader) {
        Class<?> type;
        try {
            type = Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new ExtensionException("extension class " + className + " could not be loaded, check it is on the classpath: " + e, e);
        }
        if (!Extension.class.isAssignableFrom(type)) {
            throw new ExtensionException("extension class " + className + " does not implement " + Extension.class.getName());
        }
        return type;
    }

    private static Extension instantiate(Class<?> type) {
        try {
            return (Extension) type.getConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new ExtensionException("extension class " + type.getName() + " has no public no-argument constructor", e);
        } catch (InvocationTargetException e) {
            throw new ExtensionException("extension class " + type.getName() + " threw from its constructor: " + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            throw new ExtensionException("extension class " + type.getName() + " could not be created: " + e, e);
        }
    }

    private static String nameOf(Extension extension) {
        String name;
        try {
            name = extension.name();
        } catch (RuntimeException e) {
            throw new ExtensionException("extension " + extension.getClass().getName() + " threw from name(): " + e, e);
        }
        if (name == null || !NAME.matcher(name).matches()) {
            throw new ExtensionException("extension " + extension.getClass().getName() + " has the name \"" + name + "\"; a name must be one or more lower-case letters, digits or '-'");
        }
        return name;
    }

    private static int priorityOf(Extension extension) {
        try {
            return extension.priority();
        } catch (RuntimeException e) {
            throw new ExtensionException("extension " + extension.getClass().getName() + " threw from priority(): " + e, e);
        }
    }

    static String sourceOf(Class<?> type) {
        try {
            CodeSource codeSource = type.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                return codeSource.getLocation().toString();
            }
        } catch (SecurityException ignore) {
            // the location is only reported, never used
        }
        return "unknown";
    }
}

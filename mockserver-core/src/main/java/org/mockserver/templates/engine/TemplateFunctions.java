package org.mockserver.templates.engine;

import org.mockserver.serialization.Base64Converter;

import java.util.Random;
import java.util.function.Supplier;

public class TemplateFunctions implements Supplier<Object> {
    private static final Random random = new Random();
    private static final Base64Converter base64Converter = new Base64Converter();
    private final Supplier<String> supplier;

    public TemplateFunctions(Supplier<String> supplier) {
        this.supplier = supplier;
    }

    @Override
    public String toString() {
        return supplier.get();
    }

    public static String randomInteger(int max) {
        return String.valueOf(random.nextInt(max));
    }

    public static String randomBytes(int size) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return String.valueOf(base64Converter.bytesToBase64String(bytes));
    }

    @Override
    public Object get() {
        return supplier.get();
    }
}

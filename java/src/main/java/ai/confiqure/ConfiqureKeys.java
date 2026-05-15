package ai.confiqure;

import java.util.UUID;

public final class ConfiqureKeys {
    private ConfiqureKeys() {}

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}

package batik;

import java.util.Arrays;
import java.util.List;

/**
 * Created by arneball on 2014-08-08.
 */
public interface MyInterface {
    default String def() {
        return "[" + toString() + ", " + "def]";
    }

    default String join(MyInterface other) {
        return def() + other.def();
    }
}

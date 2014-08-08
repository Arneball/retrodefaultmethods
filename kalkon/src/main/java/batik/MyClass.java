package batik;

import java.util.Arrays;
import java.util.List;

/**
 * Created by arneball on 2014-08-08.
 */
public class MyClass implements MyInterface {

    public static void main(String [] args) {
        System.out.println(new MyClass().join(new MyInterface() {
            @Override
            public String def() {
                return MyInterface.super.def().toUpperCase();
            }
        }));
        System.out.println(new Mustare().str());
    }


    static class Mustare implements TwinMeck.Child1, TwinMeck.Child2 {
        public String str() {
            return absString() + TwinMeck.Child2.super.str();
        }

        @Override
        public String absString() {
            return "lalal";
        }
    }
}

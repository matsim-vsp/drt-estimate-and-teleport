package org.matsim.run;

import org.junit.jupiter.api.Test;

public class DummyTest {
    @Test
    public final void testRunScript() {
        //TODO here is a dummy test. Add a real test later
        System.out.println("Starting dummy test");
        double x = 1;
        double y = 2;
        double z = x + y;
        assert x + y == z : "some thing is wrong!!!";
    }
}

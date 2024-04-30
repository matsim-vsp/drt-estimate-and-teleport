package org.matsim.run;

import org.matsim.application.MATSimApplication;
import picocli.CommandLine;

@CommandLine.Command(header = ":: Run base case without DRT ::", version = RunDrtEstimateAndTeleport.VERSION)
public class RunBaseCaseWithoutDrt extends MATSimApplication {
    static final String VERSION = "1.0";

    public static void main(String[] args) {
        MATSimApplication.run(RunBaseCaseWithoutDrt.class, args);
    }
}

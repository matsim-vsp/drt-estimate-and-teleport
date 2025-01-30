package org.matsim.run.mode_choic_study;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.extras.ScoringGaussianNoiseGenerator;
import picocli.CommandLine;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class VerificationRuns implements MATSimAppCommand {
    @CommandLine.Option(names = "--output", description = "root output directory", required = true)
    private String outputDirectory;

    @CommandLine.Option(names = "--iters", description = "number of iterations", defaultValue = "300")
    private int iterations;

    @CommandLine.Option(names = "--plans", description = "root output directory", defaultValue = "./scenarios/kelheim/hypothetical-plans/all-walk-plans-25pct.xml.gz")
    private String plansPath;

    @CommandLine.Option(names = "--network", description = "root output directory", defaultValue = "./scenarios/kelheim/kelheim-v3.0-drt-network.xml.gz")
    private String networkPath;

    @CommandLine.Option(names = "--mc", description = "mode choice", defaultValue = "0.1")
    private double mc;

    @CommandLine.Option(names = "--pb", description = "p_b value", defaultValue = "0.944")
    private double pb;

    public static void main(String[] args) {
        new VerificationRuns().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Config config = ConfigUtils.createConfig();
        config.network().setInputFile(networkPath);
        config.plans().setInputFile(plansPath);
        config.controller().setOutputDirectory(outputDirectory);
        config.controller().setLastIteration(iterations);
        config.replanning().setMaxAgentPlanMemorySize(2);

        // make both walk and drt being teleported
        config.routing().clearTeleportedModeParams();
        config.routing().addTeleportedModeParams(new RoutingConfigGroup.TeleportedModeParams().setMode(TransportMode.walk).setTeleportedModeSpeed(5.0));
        config.routing().addTeleportedModeParams(new RoutingConfigGroup.TeleportedModeParams().setMode(TransportMode.drt).setTeleportedModeSpeed(5.0));

        // re-planning
        double pBest = (2 * pb - 1) * (1 - mc);
        double pRnd = 1 - pBest - mc;

        config.replanning().addStrategySettings(new ReplanningConfigGroup.StrategySettings().setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.BestScore).setWeight(pBest));
        config.replanning().addStrategySettings(new ReplanningConfigGroup.StrategySettings().setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.SelectRandom).setWeight(pRnd));
        config.replanning().addStrategySettings(new ReplanningConfigGroup.StrategySettings().setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ChangeSingleTripMode).setWeight(mc));
        config.replanning().setFractionOfIterationsToDisableInnovation(0.8);

        // change mode
        config.changeMode().setModes(new String[]{TransportMode.walk, TransportMode.drt});
        config.changeMode().setIgnoreCarAvailability(true);

        // scoring
        config.scoring().setPerforming_utils_hr(6.0);
        config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("dummy").setTypicalDuration(3600));
        config.scoring().addModeParams(new ScoringConfigGroup.ModeParams(TransportMode.walk));
        config.scoring().addModeParams(new ScoringConfigGroup.ModeParams(TransportMode.drt));

        Scenario scenario = ScenarioUtils.loadScenario(config);
        Controler controler = new Controler(scenario);

        // adding the module that generates a random score for drt at the end of each iteration
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addEventHandlerBinding().toInstance(new ScoringGaussianNoiseGenerator(1.64));
            }
        });
        controler.run();

        // write down results
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of(outputDirectory + "/modestats.csv")),
                CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(true).setDelimiter(";").build())) {
            CSVPrinter resultPrinter = new CSVPrinter(new FileWriter(outputDirectory + "/main-stats.tsv"), CSVFormat.TDF);
            resultPrinter.printRecord("delta", "drt_mode_share", "memory_size", "p_b", "p_mc");
            double drtModeShare = -1;
            for (CSVRecord record : parser) {
                drtModeShare = Double.parseDouble(record.get(TransportMode.drt));
            }

            resultPrinter.printRecord(
                    Double.toString(0),
                    Double.toString(drtModeShare),
                    Double.toString(2),
                    Double.toString(pb),
                    Double.toString(mc)
            );
            resultPrinter.close();
        }
        return 0;
    }
}

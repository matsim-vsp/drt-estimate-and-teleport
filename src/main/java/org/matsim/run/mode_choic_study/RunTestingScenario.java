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
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.extras.ScoringGaussianNoiseGenerator;
import picocli.CommandLine;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RunTestingScenario implements MATSimAppCommand {
    @CommandLine.Option(names = "--sigma", description = "sigma", defaultValue = "0")
    private double sigma;

    @CommandLine.Option(names = "--deltas", description = "ASC values for DRT mode", arity = "1..*", defaultValue = "0")
    private List<Double> deltas;

    @CommandLine.Option(names = "--output", description = "root output directory", required = true)
    private String outputDirectory;

    @CommandLine.Option(names = "--iters", description = "number of iterations", defaultValue = "300")
    private int iterations;

    @CommandLine.Option(names = "--plans", description = "root output directory", defaultValue = "./scenarios/kelheim/hypothetical-plans/all-walk-plans-25pct.xml.gz")
    private String plansPath;

    @CommandLine.Option(names = "--network", description = "root output directory", defaultValue = "./scenarios/kelheim/kelheim-v3.0-drt-network.xml.gz")
    private String networkPath;

    @CommandLine.Option(names = "--drt-speed", description = "drt teleportation speed", defaultValue = "5.0")
    private double drtSpeed;

    @CommandLine.Option(names = "--drt-asc-base", description = "drt teleportation speed", defaultValue = "0.0")
    private double drtAscBase;

    public static void main(String[] args) {
        new RunTestingScenario().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(Path.of(outputDirectory))) {
            Files.createDirectories(Path.of(outputDirectory));
        }
        CSVPrinter titlePrinter = new CSVPrinter(new FileWriter(outputDirectory + "/main-stats.tsv"), CSVFormat.TDF);
        titlePrinter.printRecord("delta", "drt_mode_share", "sigma", "duplicate", "memory_size");
        titlePrinter.close();
        for (double delta : deltas) {
            String runOutputDirectory = outputDirectory + "/delta_" + delta;
            Config config = ConfigUtils.createConfig();
            config.network().setInputFile(networkPath);
            config.plans().setInputFile(plansPath);
            config.controller().setOutputDirectory(runOutputDirectory);
            config.controller().setLastIteration(iterations);

            // make both walk and drt being teleported
            config.routing().clearTeleportedModeParams();
            config.routing().addTeleportedModeParams(new RoutingConfigGroup.TeleportedModeParams().setMode(TransportMode.walk).setTeleportedModeSpeed(5.0));
            config.routing().addTeleportedModeParams(new RoutingConfigGroup.TeleportedModeParams().setMode(TransportMode.drt).setTeleportedModeSpeed(drtSpeed));

            // re-planning
            config.replanning().addStrategySettings(new ReplanningConfigGroup.StrategySettings().setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta).setWeight(0.7));
            config.replanning().addStrategySettings(new ReplanningConfigGroup.StrategySettings().setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute).setWeight(0.1));
            config.replanning().addStrategySettings(new ReplanningConfigGroup.StrategySettings().setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator).setWeight(0.1));
            config.replanning().addStrategySettings(new ReplanningConfigGroup.StrategySettings().setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ChangeSingleTripMode).setWeight(0.1));
            config.replanning().setFractionOfIterationsToDisableInnovation(0.8);

            // change mode
            config.changeMode().setModes(new String[]{TransportMode.walk, TransportMode.drt});
            config.changeMode().setIgnoreCarAvailability(true);

            // scoring
            config.scoring().setPerforming_utils_hr(6.0);
            config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("dummy").setTypicalDuration(3600));
            config.scoring().addModeParams(new ScoringConfigGroup.ModeParams(TransportMode.walk));
            config.scoring().addModeParams(new ScoringConfigGroup.ModeParams(TransportMode.drt).setConstant(delta + drtAscBase));

            Scenario scenario = ScenarioUtils.loadScenario(config);
            Controler controler = new Controler(scenario);

            // adding the module that generates a random score for drt at the end of each iteration
            controler.addOverridingModule(new AbstractModule() {
                @Override
                public void install() {
                    addEventHandlerBinding().toInstance(new ScoringGaussianNoiseGenerator(sigma));
                }
            });
            controler.run();

            // write down results
            try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of(runOutputDirectory + "/modestats.csv")),
                    CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(true).setDelimiter(";").build())) {
                double drtModeShare = -1;
                for (CSVRecord record : parser) {
                    drtModeShare = Double.parseDouble(record.get(TransportMode.drt));
                }

                CSVPrinter resultPrinter = new CSVPrinter(new FileWriter(outputDirectory + "/main-stats.tsv", true), CSVFormat.TDF);
                resultPrinter.printRecord(
                        Double.toString(delta),
                        Double.toString(drtModeShare),
                        Double.toString(sigma),
                        Double.toString(0.2),
                        Double.toString(5)
                );
                resultPrinter.close();
            }
        }


        return 0;
    }
}

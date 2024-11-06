package org.matsim.run.mode_choic_study;

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

import java.util.List;

public class RunTestingScenario implements MATSimAppCommand {
    @CommandLine.Option(names = "--sigma", description = "sigma", defaultValue = "0")
    private double sigma;

    @CommandLine.Option(names = "--deltas", description = "ASC values for DRT mode", arity = "1..*", defaultValue = "0")
    private List<Double> deltas;

    @CommandLine.Option(names = "--output", description = "root output directory", required = true)
    private String outputDirectory;


    public static void main(String[] args) {
        new RunTestingScenario().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        // TODO write down main results.tsv/csv


        for (double delta : deltas) {
            Config config = ConfigUtils.createConfig();
            config.network().setInputFile("/scenarios/kelheim/kelheim-v3.0-drt-network.xml.gz");
            config.plans().setInputFile("/scenarios/kelheim/hypothetical-plans/all-walk-plans-25pct.xml.gz");
            config.controller().setOutputDirectory(outputDirectory + "/delta_" + delta);
            config.controller().setLastIteration(300);

            // make both walk and bike being teleported at the same speed
            config.routing().clearTeleportedModeParams();
            config.routing().addTeleportedModeParams(new RoutingConfigGroup.TeleportedModeParams().setMode(TransportMode.walk).setTeleportedModeSpeed(5.0));
            config.routing().addTeleportedModeParams(new RoutingConfigGroup.TeleportedModeParams().setMode(TransportMode.drt).setTeleportedModeSpeed(5.0));

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
            config.scoring().addModeParams(new ScoringConfigGroup.ModeParams(TransportMode.drt).setConstant(delta));

            Scenario scenario = ScenarioUtils.loadScenario(config);
            Controler controler = new Controler(scenario);

            // adding the module that generates a random score for bike at the end of each iteration
            controler.addOverridingModule(new AbstractModule() {
                @Override
                public void install() {
                    addEventHandlerBinding().toInstance(new ScoringGaussianNoiseGenerator(sigma));
                }
            });
            controler.run();
        }

        return 0;
    }
}

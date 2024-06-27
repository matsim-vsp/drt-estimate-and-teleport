package org.matsim.run;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.MATSimApplication;
import org.matsim.contrib.drt.estimator.DrtEstimatorModule;
import org.matsim.contrib.drt.estimator.impl.DirectTripBasedDrtEstimator;
import org.matsim.contrib.drt.estimator.impl.distribution.NormalDistributionGenerator;
import org.matsim.contrib.drt.estimator.impl.trip_estimation.ConstantRideDurationEstimator;
import org.matsim.contrib.drt.estimator.impl.waiting_time_estimation.ConstantWaitingTimeEstimator;
import org.matsim.contrib.drt.estimator.impl.waiting_time_estimation.ShapeFileBasedWaitingTimeEstimator;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.*;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.extras.ScoringGaussianNoiseGenerator;
import picocli.CommandLine;

import javax.annotation.Nullable;

@CommandLine.Command(header = ":: Run experiments on noise ::", version = ExperimentsOnRandomness.VERSION)
public class ExperimentsOnRandomness extends MATSimApplication {
    @CommandLine.Option(names = "--sigma", description = "standard deviation of ride duration", defaultValue = "1.0")
    private double sigma;

    static final String VERSION = "1.0";

    public static void main(String[] args) {
        MATSimApplication.run(ExperimentsOnRandomness.class, args);
    }

    @Nullable
    @Override
    protected Config prepareConfig(Config config) {
        ConfigUtils.addOrGetModule(config, DvrpConfigGroup.class);
        MultiModeDrtConfigGroup multiModeDrtConfig = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
        DrtConfigs.adjustMultiModeDrtConfig(multiModeDrtConfig, config.scoring(), config.routing());
        config.qsim().setFlowCapFactor(10000);
        config.qsim().setStorageCapFactor(10000);

        for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
            drtCfg.simulationType = DrtConfigGroup.SimulationType.estimateAndTeleport;
        }
        return config;
    }

    @Override
    protected void prepareScenario(Scenario scenario) {
        scenario.getPopulation()
                .getFactory()
                .getRouteFactories()
                .setRouteFactory(DrtRoute.class, new DrtRouteFactory());
    }


    @Override
    protected void prepareControler(Controler controler) {
        Config config = controler.getConfig();
        MultiModeDrtConfigGroup multiModeDrtConfig = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
        controler.addOverridingModule(new DvrpModule());
        controler.addOverridingModule(new MultiModeDrtModule());
        controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(multiModeDrtConfig));

        // Add artificial noise in score
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addEventHandlerBinding().toInstance(new ScoringGaussianNoiseGenerator(sigma));
            }
        });

        // add the DRT estimator binding
        MultiModeDrtConfigGroup multiModeDrtConfigGroup = MultiModeDrtConfigGroup.get(config);
        for (DrtConfigGroup drtCfg : multiModeDrtConfigGroup.getModalElements()) {
            controler.addOverridingModule(new AbstractModule() {
                @Override
                public void install() {
                    // No variation in the DRT estimator part (std set to 0)
                    DrtEstimatorModule.bindEstimator(binder(), drtCfg.mode).toInstance(
                            new DirectTripBasedDrtEstimator.Builder()
                                    .setWaitingTimeEstimator(new ConstantWaitingTimeEstimator(300))
                                    .setWaitingTimeDistributionGenerator(new NormalDistributionGenerator(1, 0))
                                    .setRideDurationEstimator(new ConstantRideDurationEstimator(1.25, 300))
                                    .setRideDurationDistributionGenerator(new NormalDistributionGenerator(2, 0))
                                    .build()
                    );
                }
            });
        }
    }
}

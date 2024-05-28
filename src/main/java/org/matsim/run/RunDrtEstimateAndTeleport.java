package org.matsim.run;

import org.matsim.api.core.v01.Scenario;
import org.matsim.application.MATSimApplication;
import org.matsim.contrib.drt.estimator.DrtEstimatorModule;
import org.matsim.contrib.drt.estimator.impl.NetworkBasedDrtEstimator;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtConfigs;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtModule;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import picocli.CommandLine;

import javax.annotation.Nullable;

@CommandLine.Command(header = ":: Run estimate and teleport ::", version = RunDrtEstimateAndTeleport.VERSION)
public class RunDrtEstimateAndTeleport extends MATSimApplication {
    @CommandLine.Option(names = "--ride-time-alpha", description = "standard deviation of ride duration", defaultValue = "1.25")
    private double rideTimeAlpha;

    @CommandLine.Option(names = "--ride-time-beta", description = "standard deviation of ride duration", defaultValue = "300")
    private double rideTimeBeta;

    @CommandLine.Option(names = "--ride-time-std", description = "standard deviation of ride duration", defaultValue = "0.25")
    private double rideTimeStd;

    @CommandLine.Option(names = "--wait-time-mean", description = "standard deviation of ride duration", defaultValue = "300")
    private double meanWaitTime;

    @CommandLine.Option(names = "--wait-time-std", description = "standard deviation of waiting time", defaultValue = "0.25")
    private double waitTimeStd;

    static final String VERSION = "1.0";

    public static void main(String[] args) {
        MATSimApplication.run(RunDrtEstimateAndTeleport.class, args);
    }

    @Nullable
    @Override
    protected Config prepareConfig(Config config) {
        ConfigUtils.addOrGetModule(config, DvrpConfigGroup.class);
        MultiModeDrtConfigGroup multiModeDrtConfig = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
        DrtConfigs.adjustMultiModeDrtConfig(multiModeDrtConfig, config.scoring(), config.routing());
        for (DrtConfigGroup drtCfg: multiModeDrtConfig.getModalElements()) {
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

        MultiModeDrtConfigGroup multiModeDrtConfigGroup = MultiModeDrtConfigGroup.get(config);
        for (DrtConfigGroup drtCfg : multiModeDrtConfigGroup.getModalElements()) {
            // add the DRT estimator binding
            controler.addOverridingModule(new AbstractModule() {
                @Override
                public void install() {
                    DrtEstimatorModule.bindEstimator(binder(), drtCfg.mode).toInstance(NetworkBasedDrtEstimator.
                            normalDistributedNetworkBasedDrtEstimator(rideTimeAlpha, rideTimeBeta, rideTimeStd,
                                    meanWaitTime, waitTimeStd));
                }
            });
        }
    }

}

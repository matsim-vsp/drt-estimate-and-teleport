package org.matsim.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.drt.estimator.DrtEstimatorModule;
import org.matsim.contrib.drt.estimator.impl.DirectTripBasedDrtEstimator;
import org.matsim.contrib.drt.estimator.impl.distribution.NormalDistributionGenerator;
import org.matsim.contrib.drt.estimator.impl.trip_estimation.ConstantRideDurationEstimator;
import org.matsim.contrib.drt.estimator.impl.waiting_time_estimation.ConstantWaitingTimeEstimator;
import org.matsim.contrib.drt.estimator.impl.waiting_time_estimation.ShapeFileBasedWaitingTimeEstimator;
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
import org.matsim.simwrapper.SimWrapperModule;
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

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    static final String VERSION = "1.0";

    private static final Logger log = LogManager.getLogger(RunDrtEstimateAndTeleport.class);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        MATSimApplication.run(RunDrtEstimateAndTeleport.class, args);
        long endTime = System.currentTimeMillis();
        long elapsedTime = (endTime - startTime) / 1000;
        log.info("Time used = {} seconds", elapsedTime);
    }

    @Nullable
    @Override
    protected Config prepareConfig(Config config) {
        ConfigUtils.addOrGetModule(config, DvrpConfigGroup.class);
        MultiModeDrtConfigGroup multiModeDrtConfig = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
        DrtConfigs.adjustMultiModeDrtConfig(multiModeDrtConfig, config.scoring(), config.routing());
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
        Network network = controler.getScenario().getNetwork();
        MultiModeDrtConfigGroup multiModeDrtConfig = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
        controler.addOverridingModule(new DvrpModule());
        controler.addOverridingModule(new MultiModeDrtModule());
        controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(multiModeDrtConfig));
        controler.addOverridingModule(new SimWrapperModule());

        MultiModeDrtConfigGroup multiModeDrtConfigGroup = MultiModeDrtConfigGroup.get(config);
        for (DrtConfigGroup drtCfg : multiModeDrtConfigGroup.getModalElements()) {
            // add the DRT estimator binding
            if (shp.isDefined()) {
                // ShapeFile-based waiting time estimator will be used
                controler.addOverridingModule(new AbstractModule() {
                    @Override
                    public void install() {
                        DrtEstimatorModule.bindEstimator(binder(), drtCfg.mode).toInstance(
                                new DirectTripBasedDrtEstimator.Builder()
                                        .setWaitingTimeEstimator(new ShapeFileBasedWaitingTimeEstimator(network, shp.readFeatures(), meanWaitTime))
                                        .setWaitingTimeDistributionGenerator(new NormalDistributionGenerator(1, waitTimeStd))
                                        .setRideDurationEstimator(new ConstantRideDurationEstimator(rideTimeAlpha, rideTimeBeta))
                                        .setRideDurationDistributionGenerator(new NormalDistributionGenerator(2, rideTimeStd))
                                        .build()
                        );
                    }
                });
            } else {
                // otherwise, standard waiting time estimator will be used
                controler.addOverridingModule(new AbstractModule() {
                    @Override
                    public void install() {
                        DrtEstimatorModule.bindEstimator(binder(), drtCfg.mode).toInstance(
                                new DirectTripBasedDrtEstimator.Builder()
                                        .setWaitingTimeEstimator(new ConstantWaitingTimeEstimator(meanWaitTime))
                                        .setWaitingTimeDistributionGenerator(new NormalDistributionGenerator(1, waitTimeStd))
                                        .setRideDurationEstimator(new ConstantRideDurationEstimator(rideTimeAlpha, rideTimeBeta))
                                        .setRideDurationDistributionGenerator(new NormalDistributionGenerator(2, rideTimeStd))
                                        .build()
                        );
                    }
                });
            }


        }
    }

}

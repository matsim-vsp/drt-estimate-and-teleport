package org.matsim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.common.zones.Zone;
import org.matsim.contrib.common.zones.ZoneSystem;
import org.matsim.contrib.common.zones.systems.grid.square.SquareGridZoneSystem;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileWriter;
import picocli.CommandLine;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import static org.matsim.utils.gis.shp2matsim.ShpGeometryUtils.loadPreparedGeometries;

public class ResultingDemandAnalysis implements MATSimAppCommand {
    @CommandLine.Option(names = "--config", description = "config file", required = true)
    private String runConfig;

    @CommandLine.Option(names = "--directory", description = "path to output directory. By default: use output directory in config file", defaultValue = "")
    private String directory;

    @CommandLine.Option(names = "--iterations", description = "number of last iteration", defaultValue = "0")
    private String iterationFolder;

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    @CommandLine.Option(names = "--cell-size", description = "cell size for the analysis", defaultValue = "200")
    private double cellSize;

    private static final Logger log = LogManager.getLogger(ResultingDemandAnalysis.class);

    public static void main(String[] args) {
        new ResultingDemandAnalysis().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Config config = ConfigUtils.loadConfig(runConfig, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();
        DrtConfigGroup drtConfigGroup = DrtConfigGroup.getSingleModeDrtConfig(config);

        if (directory.equals("")) {
            directory = config.controller().getOutputDirectory();
        }

        Path servedDemandsFile = ApplicationUtils.globFile(Path.of(directory + "/ITERS/it." + iterationFolder), "*drt_legs_drt.csv*");
        Path rejectedDemandsFile = ApplicationUtils.globFile(Path.of(directory + "/ITERS/it." + iterationFolder), "*drt_rejections_drt.csv*");

        // Create Zonal system from Grid
        List<PreparedGeometry> serviceAreas;
        if (shp.isDefined()) {
            URL url = URI.create(shp.getShapeFile()).toURL();
            serviceAreas = (loadPreparedGeometries(url));
        } else {
            serviceAreas = null;
        }

        ZoneSystem zonalSystem;
        if (serviceAreas != null) {
            Predicate<Zone> zoneFilter = zone -> serviceAreas.stream().anyMatch(area -> area.intersects(Objects.requireNonNull(zone.getPreparedGeometry()).getGeometry()));
            zonalSystem = new SquareGridZoneSystem(network, cellSize, zoneFilter);
        } else {
            zonalSystem = new SquareGridZoneSystem(network, cellSize);
        }

        Map<String, List<Double>> statsMap = new HashMap<>();
        // Process rejected requests
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(rejectedDemandsFile),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                double departureTime = Double.parseDouble(record.get("time"));
                Link fromLink = network.getLinks().get(Id.createLinkId(record.get("fromLinkId")));
                Link toLink = network.getLinks().get(Id.createLinkId(record.get("toLinkId")));
                Optional<Zone> zone = zonalSystem.getZoneForLinkId(fromLink.getId());
                if (zone.isEmpty()) {
                    log.error("cannot find zone for link " + fromLink.getId().toString());
                } else {
                    statsMap.computeIfAbsent(zone.get().getId().toString(), l -> new ArrayList<>()).add(0.);
                }
            }
        }

        // Process served DRT trips
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(servedDemandsFile),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                double departureTime = Double.parseDouble(record.get("departureTime"));
                double arrivalTime = Double.parseDouble(record.get("arrivalTime"));
                double journeyTime = arrivalTime - departureTime;
                Link fromLink = network.getLinks().get(Id.createLinkId(record.get("fromLinkId")));
                Link toLink = network.getLinks().get(Id.createLinkId(record.get("toLinkId")));

                Optional<Zone> zone = zonalSystem.getZoneForLinkId(fromLink.getId());
                if (zone.isEmpty()) {
                    log.error("cannot find zone for link " + fromLink.getId().toString());
                } else {
                    statsMap.computeIfAbsent(zone.get().getId().toString(), l -> new ArrayList<>()).add(1.);
                }
            }
        }


        // Write shp file
        if (!Files.exists(Path.of(directory + "/resulting-demands-analysis/"))) {
            Files.createDirectories(Path.of(directory + "/resulting-demands-analysis/"));
        }
        String crs = shp.getShapeCrs();
        Collection<SimpleFeature> features = convertGeometriesToSimpleFeatures(crs, zonalSystem, statsMap);
        ShapeFileWriter.writeGeometries(features, directory + "/resulting-demands-analysis/trips_analysis.shp");
        return 0;
    }


    private Collection<SimpleFeature> convertGeometriesToSimpleFeatures(String targetCoordinateSystem, ZoneSystem zones, Map<String, List<Double>> statsMap) {
        SimpleFeatureTypeBuilder simpleFeatureBuilder = new SimpleFeatureTypeBuilder();
        try {
            simpleFeatureBuilder.setCRS(MGC.getCRS(targetCoordinateSystem));
        } catch (IllegalArgumentException e) {
            log.warn("Coordinate reference system \""
                    + targetCoordinateSystem
                    + "\" is unknown! ");
        }

        simpleFeatureBuilder.setName("drtZoneFeature");
        // note: column names may not be longer than 10 characters. Otherwise, the name is cut after the 10th character and the value is NULL in QGis
        simpleFeatureBuilder.add("the_geom", Polygon.class);
        simpleFeatureBuilder.add("zoneIid", String.class);
        simpleFeatureBuilder.add("centerX", Double.class);
        simpleFeatureBuilder.add("centerY", Double.class);
        simpleFeatureBuilder.add("nRequests", Integer.class);
        simpleFeatureBuilder.add("pctServed", Double.class);
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(simpleFeatureBuilder.buildFeatureType());

        Collection<SimpleFeature> features = new ArrayList<>();

        for (Zone zone : zones.getZones().values()) {
            Object[] featureAttributes = new Object[6];
            Geometry geometry = zone.getPreparedGeometry() != null ? zone.getPreparedGeometry().getGeometry() : null;
            featureAttributes[0] = geometry;
            featureAttributes[1] = zone.getId();
            featureAttributes[2] = zone.getCentroid().getX();
            featureAttributes[3] = zone.getCentroid().getY();

            int numDeparture = statsMap.getOrDefault(zone.getId().toString(), new ArrayList<>()).size();
            featureAttributes[4] = numDeparture;
            if (numDeparture == 0) {
                featureAttributes[5] = Double.NaN;
            } else {
                featureAttributes[5] = statsMap.get(zone.getId().toString()).stream().mapToDouble(v -> v).sum() / numDeparture;
            }

            try {
                features.add(builder.buildFeature(zone.toString(), featureAttributes));
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        return features;
    }
}

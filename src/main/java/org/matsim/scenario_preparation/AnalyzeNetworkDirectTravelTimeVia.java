package org.matsim.scenario_preparation;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CrsOptions;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import picocli.CommandLine;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class AnalyzeNetworkDirectTravelTimeVia implements MATSimAppCommand {
    @CommandLine.Option(names = "--network", description = "path to network file", required = true)
    private String networkFile;

    @CommandLine.Option(names = "--input", description = "input files", required = true)
    private Path processedTripsFile;

    @CommandLine.Option(names = "--output", description = "path to processed trips file", required = true)
    private Path outputPath;

    @CommandLine.Mixin
    private CrsOptions crs = new CrsOptions();

    public static void main(String[] args) {
        new AnalyzeNetworkDirectTravelTimeVia().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Network network = NetworkUtils.readNetwork(networkFile);

        // only keep car links
        Set<Id<Link>> linksToRemove = new HashSet<>();
        for (Link link : network.getLinks().values()) {
            if (!link.getAllowedModes().contains(TransportMode.car)) {
                linksToRemove.add(link.getId());
            }
        }
        linksToRemove.forEach(network::removeLink);
        NetworkCleaner networkCleaner = new NetworkCleaner();
        networkCleaner.run(network);

        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new TimeAsTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

        CSVPrinter tsvPrinter = new CSVPrinter(new FileWriter(outputPath.toString()), CSVFormat.TDF);

        // Read through processed trip file
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(processedTripsFile),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            // prepare header for the new tsv file
            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            headers.add("direct_trip_duration");
            tsvPrinter.printRecord(headers);

            for (CSVRecord record : parser.getRecords()) {
                if (!record.get("Request.Status").equals("Completed")){
                    continue;
                }
                Coord fromCoord = new Coord(Double.parseDouble(record.get("Origin.Lng")), Double.parseDouble(record.get("Origin.Lat")));
                Coord toCoord = new Coord(Double.parseDouble(record.get("Destination.Lng")), Double.parseDouble(record.get("Destination.Lat")));
                fromCoord = crs.getTransformation().transform(fromCoord);
                toCoord = crs.getTransformation().transform(toCoord);
                double departureTime = 0.;

                Link fromLink = NetworkUtils.getNearestLink(network, fromCoord);
                Link toLink = NetworkUtils.getNearestLink(network, toCoord);
                double directTripTravelTime = VrpPaths.calcAndCreatePath(fromLink, toLink, departureTime, router, travelTime).getTravelTime();
                List<String> outputRow = new java.util.ArrayList<>(record.stream().toList());
                outputRow.add(Double.toString(directTripTravelTime));
                tsvPrinter.printRecord(outputRow);
            }
        }
        tsvPrinter.close();
        return 0;
    }
}

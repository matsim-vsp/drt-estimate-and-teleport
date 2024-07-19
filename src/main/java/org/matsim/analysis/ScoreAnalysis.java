package org.matsim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.DefaultAnalysisMainModeIdentifier;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ScoreAnalysis {

    public static void main(String[] args) throws IOException {
        Population plans = PopulationUtils.readPopulation(args[0]);
        MainModeIdentifier modeIdentifier = new DefaultAnalysisMainModeIdentifier();
        List<Double> deltas = new ArrayList<>();
        for (Person person : plans.getPersons().values()) {
            double drtScore = Double.NaN;
            double bestNonDrtScore = Double.MIN_VALUE;
            for (Plan plan : person.getPlans()) {
                double score = plan.getScore();
                TripStructureUtils.Trip trip = TripStructureUtils.getTrips(plan).get(0);
                String mode = modeIdentifier.identifyMainMode(trip.getTripElements());
                if (mode.equals(TransportMode.drt)) {
                    drtScore = score;
                    continue;
                }

                if (score > bestNonDrtScore) {
                    bestNonDrtScore = score;
                }
            }
            double delta = drtScore - bestNonDrtScore;
            deltas.add(delta);
        }

        // write down delta
        CSVPrinter tsvPrinter = new CSVPrinter(new FileWriter(args[1]), CSVFormat.TDF);
        tsvPrinter.printRecord("delta");
        for (double delta : deltas) {
            tsvPrinter.printRecord(Double.toString(delta));
        }
        tsvPrinter.close();

    }
}

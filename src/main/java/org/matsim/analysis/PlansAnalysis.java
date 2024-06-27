package org.matsim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang.mutable.MutableInt;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.PopulationUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PlansAnalysis {
    public static void main(String[] args) throws IOException {
        Population plans = PopulationUtils.readPopulation(args[0]);
        Map<Integer, MutableInt> drtPlansCounts = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            drtPlansCounts.put(i, new MutableInt());
        }

        for (Person person : plans.getPersons().values()) {
            int numDrtPlans = 0;
            // Note: here we assume each person only has one leg per day!
            for (Plan plan : person.getPlans()) {
                for (PlanElement planElement : plan.getPlanElements()) {
                    if (planElement instanceof Leg) {
                        if (((Leg) planElement).getMode().equals(TransportMode.drt)) {
                            numDrtPlans++;
                        }
                    }
                }
            }
            drtPlansCounts.get(numDrtPlans).increment();
        }

        // write down results in a tsv file
        CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(args[1]), CSVFormat.TDF);
        csvPrinter.printRecord("number_of_drt_plans", "count");
        for (Integer i : drtPlansCounts.keySet()) {
            csvPrinter.printRecord(Integer.toString(i), Integer.toString(drtPlansCounts.get(i).intValue()));
        }
        csvPrinter.close();
    }
}

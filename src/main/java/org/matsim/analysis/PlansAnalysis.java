package org.matsim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang.mutable.MutableInt;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.population.PopulationUtils;
import picocli.CommandLine;

import java.io.FileWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.matsim.application.ApplicationUtils.globFile;

public class PlansAnalysis implements MATSimAppCommand {
    @CommandLine.Option(names = "--asc", description = "asc values", defaultValue = "0.0", split = ",")
    private List<String> asc;

    @CommandLine.Option(names = "--path-front", description = "common part of the path (front)", required = true)
    private String commonPathFront;

    @CommandLine.Option(names = "--path-rear", description = "common part of the path (rear)", defaultValue = "")
    private String commonPathRear;

    @CommandLine.Option(names = "--iterations", description = "number of iterations runned", defaultValue = "1000")
    private int iterations;

    @CommandLine.Option(names = "--interval", description = "interval of writing out intermediate plans", defaultValue = "50")
    private int interval;

    @CommandLine.Option(names = "--memory-size", description = "max number of plans stored in agent's memory", defaultValue = "5")
    private int memorySize;

    public static void main(String[] args) {
        new PlansAnalysis().execute(args);
    }

    @Override
    public Integer call() throws Exception {

        for (String asc : asc) {
            String outputFolder = commonPathFront + asc + commonPathRear;
            Map<Integer, Map<Integer, MutableInt>> counterMap = new HashMap<>();

            // intermediate iterations
            for (int i = 0; i < iterations; i += interval) {
                String iterationsFolder = outputFolder + "/ITERS/it." + i;
                String plansFile = globFile(Path.of(iterationsFolder), "*.plans.xml.gz*").toString();
                analyzeFromPlans(counterMap, i, plansFile);
            }

            // final output plans
            String plansFile = globFile(Path.of(outputFolder), "*output_plans.xml.gz*").toString();
            analyzeFromPlans(counterMap, iterations, plansFile);

            // write down results in a tsv file
            CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(outputFolder + "/output_plan_analysis.tsv"), CSVFormat.TDF);
            csvPrinter.printRecord("iteration", "num_of_drt_plans_in_memory", "count");
            for (int i = 0; i <= iterations; i += interval) {
                Map<Integer, MutableInt> drtPlansCountsForIteration = counterMap.get(i);
                for (Integer j : drtPlansCountsForIteration.keySet()) {
                    csvPrinter.printRecord(Integer.toString(i), Integer.toString(j), Integer.toString(drtPlansCountsForIteration.get(j).intValue()));
                }
            }
            csvPrinter.close();
        }
        return 0;
    }

    private void analyzeFromPlans(Map<Integer, Map<Integer, MutableInt>> counterMap, int i, String plansFile) {
        Population plans = PopulationUtils.readPopulation(plansFile);
        Map<Integer, MutableInt> drtPlansCountsForIteration = new HashMap<>();
        for (int j = 0; j < memorySize + 1; j++) {
            // In the output plans, there may be one extra plan for some agents.
            // For those agents, we will remove the worst plan.
            // So, we will only count up to memorySize
            drtPlansCountsForIteration.put(j, new MutableInt());
        }

        for (Person person : plans.getPersons().values()) {
            int numDrtPlans = getNumDrtPlans(person);
            drtPlansCountsForIteration.get(numDrtPlans).increment();
        }

        counterMap.put(i, drtPlansCountsForIteration);
    }

    private int getNumDrtPlans(Person person) {
        // remove the worst plan if there are more plans than the memory size
        if (person.getPlans().size() > memorySize) {
            double wostScore = Double.POSITIVE_INFINITY;
            Plan worstPlan = null;
            for (Plan plan : person.getPlans()) {
                double score = plan.getScore();
                if (score < wostScore) {
                    worstPlan = plan;
                    wostScore = score;
                }
            }
            person.removePlan(worstPlan);
        }

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
        return numDrtPlans;
    }
}

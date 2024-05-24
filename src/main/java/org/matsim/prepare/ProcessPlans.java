package org.matsim.prepare;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.PopulationUtils;

public class ProcessPlans {
    public static void main(String[] args) {
        Population plans = PopulationUtils.readPopulation("/Users/luchengqi/Documents/MATSimScenarios/Kelheim/drt-estimate-and-teleport/hypothetical-plans/all-drt-plans-25pct.xml.gz");

        // Generate an all-walk initial plan
        for (Person person: plans.getPersons().values()) {
            for (PlanElement planElement : person.getSelectedPlan().getPlanElements()){
                if (planElement instanceof Leg){
                    ((Leg) planElement).setMode(TransportMode.walk);
                }
            }
        }
        new PopulationWriter(plans).write("/Users/luchengqi/Documents/MATSimScenarios/Kelheim/drt-estimate-and-teleport/hypothetical-plans/all-walk-plans-25pct.xml.gz");

        // Generate an all-car initial plan
        for (Person person: plans.getPersons().values()) {
            for (PlanElement planElement : person.getSelectedPlan().getPlanElements()){
                if (planElement instanceof Leg){
                    ((Leg) planElement).setMode(TransportMode.car);
                }
            }
        }
        new PopulationWriter(plans).write("/Users/luchengqi/Documents/MATSimScenarios/Kelheim/drt-estimate-and-teleport/hypothetical-plans/all-car-plans-25pct.xml.gz");

        // Generate an all-bike initial plan
        for (Person person: plans.getPersons().values()) {
            for (PlanElement planElement : person.getSelectedPlan().getPlanElements()){
                if (planElement instanceof Leg){
                    ((Leg) planElement).setMode(TransportMode.bike);
                }
            }
        }
        new PopulationWriter(plans).write("/Users/luchengqi/Documents/MATSimScenarios/Kelheim/drt-estimate-and-teleport/hypothetical-plans/all-bike-plans-25pct.xml.gz");
    }
}

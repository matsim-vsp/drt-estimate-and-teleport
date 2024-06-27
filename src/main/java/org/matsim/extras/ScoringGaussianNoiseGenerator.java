package org.matsim.extras;

import com.google.inject.Inject;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonScoreEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;

import java.util.Random;

public class ScoringGaussianNoiseGenerator implements PersonDepartureEventHandler {
    private final double std;
    private final Random random;

    @Inject
    EventsManager events;

    public ScoringGaussianNoiseGenerator(double std) {
        this.std = std;
        this.random = new Random(1);
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        if (event.getLegMode().equals(TransportMode.drt)) {
            // Add a random value to the agent
            double extraScore = random.nextGaussian() * std;
            events.processEvent(new PersonScoreEvent(event.getTime(), event.getPersonId(), extraScore, "artificial noise in score"));
        }
    }
}

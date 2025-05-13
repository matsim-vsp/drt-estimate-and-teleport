package org.matsim.extras;

import com.google.inject.Inject;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonScoreEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;

import java.util.Random;

/**
 * Generate a discrete noise (a pre-defined magnitude, either positive or negative)
 */
public class DiscreteNoiseGenerator implements PersonDepartureEventHandler {
    private final double magnitude;
    private final Random random;

    @Inject
    EventsManager events;

    public DiscreteNoiseGenerator(double magnitude, Random random) {
        this.magnitude = magnitude;
        this.random = random;
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        if (event.getLegMode().equals(TransportMode.drt)) {
            // Add a random value to the agent
            double extraScore = random.nextBoolean() ? magnitude : -1 * magnitude;
            events.processEvent(new PersonScoreEvent(event.getTime(), event.getPersonId(), extraScore, "artificial noise in score"));
        }
    }
}

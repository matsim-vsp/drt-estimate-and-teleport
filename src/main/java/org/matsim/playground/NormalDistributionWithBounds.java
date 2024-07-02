package org.matsim.playground;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NormalDistributionWithBounds {

    public static void main(String[] args) {
        Random random = new Random();
        double lowerBound = -1.0;
        double upperBound = 2.0;

        List<Double> randomNumbers = new ArrayList<>();
        for (int i = 0; i < 1000000; i++) {
            double randomNumber = random.nextGaussian();
            if (randomNumber > upperBound) {
                randomNumber = upperBound;
            }
            if (randomNumber < lowerBound) {
                randomNumber = lowerBound;
            }
            randomNumbers.add(randomNumber);
        }

        double average = randomNumbers.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        System.out.printf("The average is " + average);
    }
}

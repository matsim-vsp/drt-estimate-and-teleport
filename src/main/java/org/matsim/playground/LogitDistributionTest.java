package org.matsim.playground;

import java.util.Random;

public class LogitDistributionTest {
    public static void main(String[] args) {
        Random random = new Random();

        double x1 = 0;
        double x2 = 1;
        double x3 = -1;
        double drtBase = 3;

        double beta = 1;
        double sigma = 1;

        double n = 0;
        double population = 10000;

        for (int i = 0; i < population; i++) {
            double disturbance = random.nextGaussian() * sigma;
            double drt = drtBase + disturbance;
            double p = Math.exp(beta * drt) / (Math.exp(beta * drt) + Math.exp(beta * x1) + Math.exp(beta * x2) + Math.exp(beta * x3));
            n += p;
        }

        System.out.println("The expected number is " + n);
        System.out.println("The expected mode share " + n / population * 100 + "%");

    }
}

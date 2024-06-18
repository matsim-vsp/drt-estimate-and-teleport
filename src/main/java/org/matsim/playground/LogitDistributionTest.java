package org.matsim.playground;

import java.util.Random;

public class LogitDistributionTest {
    public static void main(String[] args) {
        Random random = new Random();

        double x1 = -0.6;
        double x2 = -3;

        double beta = 1;

        double n = 0;
        double population = 10000;

        for (int i = 0; i < population; i++) {
            double x = 0.001 * random.nextGaussian();
//            double x = 0;
            n += Math.exp(beta * x) / (Math.exp(beta * x) + Math.exp(beta * x1) + Math.exp(beta * x2));
        }

        System.out.println("The expected number is " + n);
        System.out.println("The expected mode share " + n / population * 100 + "%");

    }
}

package simblock.test;

import org.apache.commons.math3.distribution.BinomialDistribution;

import java.util.Random;


public class SortitionTests {

    public static void main(String[] args) {

        Random r = new Random();
        double totalCoins = 1000;

        double binomialP = 20 / totalCoins;

        int selections = 0;
        selections += getSelected(100, binomialP, r.nextDouble());
        selections += getSelected(100, binomialP, r.nextDouble());
        selections += getSelected(100, binomialP, r.nextDouble());
        selections += getSelected(100, binomialP, r.nextDouble());
        selections += getSelected(100, binomialP, r.nextDouble());
        selections += getSelected(100, binomialP, r.nextDouble());
        selections += getSelected(100, binomialP, r.nextDouble());
        selections += getSelected(100, binomialP, r.nextDouble());
        selections += getSelected(100, binomialP, r.nextDouble());
        selections += getSelected(100, binomialP, r.nextDouble());

        System.out.println("Expected 20, got: "+selections+" selections.");

    }

    private static int getSelected(double n, double p, double r) {
        // binomial cdf walk
        BinomialDistribution dist = new BinomialDistribution((int)Math.round(n), p);
        int j;
        for(j = 0; j < n; j++) {
            double boundary = dist.cumulativeProbability(j);

            if(r <= boundary) {
                return j;
            }
        }
        return j;
    }

}

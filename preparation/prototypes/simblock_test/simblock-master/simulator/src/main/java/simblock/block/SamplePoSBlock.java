/*
 * Copyright 2019 Distributed Systems Group
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package simblock.block;

import static simblock.settings.SimulationConfiguration.AVERAGE_COINS;
import static simblock.settings.SimulationConfiguration.STAKING_REWARD;
import static simblock.settings.SimulationConfiguration.STDEV_OF_COINS;
import static simblock.simulator.Main.random;
import static simblock.simulator.Simulator.getSimulatedNodes;
import static simblock.simulator.Simulator.getTargetInterval;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import simblock.node.Node;

/**
 * The type Sample proof of stake block.
 */
public class SamplePoSBlock extends Block {
  private final Map<Node, Coinage> coinages;
  private static Map<Node, Coinage> genesisCoinages;
  private final double difficulty;
  private final double totalDifficulty;
  private final double nextDifficulty;
  private final double totalCoinage;

  /**
   * Instantiates a new Sample proof of stake block.
   *
   * @param parent     the parent
   * @param minter     the minter
   * @param time       the time
   * @param difficulty the difficulty
   */
  public SamplePoSBlock(
      SamplePoSBlock parent, Node minter, long time, double difficulty
  ) {
    super(parent, minter, time);

    this.coinages = new HashMap<>();
    if (parent == null) {
      for (Node node : getSimulatedNodes()) {
        this.coinages.put(node, genesisCoinages.get(node).clone());
      }
    } else {
      for (Node node : getSimulatedNodes()) {
        this.coinages.put(node, parent.getCoinage(node).clone());
        this.coinages.get(node).increaseAge();
      }
      this.coinages.get(minter).reward(STAKING_REWARD);
      this.coinages.get(minter).resetAge();
    }

    double tc = 0;
    for (Node node : getSimulatedNodes()) {
      tc = tc + this.coinages.get(node).getCoinage();
    }
    this.totalCoinage = tc;

    this.difficulty = difficulty;
    if (parent == null) {
      this.totalDifficulty = difficulty;
    } else {
      this.totalDifficulty = parent.getTotalDifficulty() + difficulty;
    }
    this.nextDifficulty = (tc * getTargetInterval()) / 1000;
  }

  /**
   * Gets coinage.
   *
   * @param node the node
   * @return the coinage
   */
  //TODO Coinage is related to proof of stake obviously
  public Coinage getCoinage(Node node) {
    return this.coinages.get(node);
  }

  public Map<Node, Coinage> getCoinages() {
    return this.coinages;
  }

  public double getTotalCoinage() {
    return this.totalCoinage;
  }

  /**
   * Gets difficulty.
   *
   * @return the difficulty
   */
  public double getDifficulty() {
    return this.difficulty;
  }

  /**
   * Gets total difficulty.
   *
   * @return the total difficulty
   */
  public double getTotalDifficulty() {
    return this.totalDifficulty;
  }

  /**
   * Gets next difficulty.
   *
   * @return the next difficulty
   */
  public double getNextDifficulty() {
    return this.nextDifficulty;
  }

  private static Coinage genCoinage() {
    double r = random.nextGaussian();
    double coins = Math.max(((r * STDEV_OF_COINS + AVERAGE_COINS)), 0);
    return new Coinage(coins, 1);
  }

  /**
   * Genesis block sample proof of stake block.
   *
   * @param minter the minter
   * @return the sample proof of stake block
   */
  public static SamplePoSBlock genesisBlock(Node minter) {
    genesisCoinages = new HashMap<>();
    for (Node node : getSimulatedNodes()) {
      genesisCoinages.put(node, genCoinage());
    }
    return new SamplePoSBlock(null, minter, 0, 0);
  }
}

package simblock.task.algorand;

import simblock.block.Block;
import simblock.node.Node;
import simblock.task.AbstractMessageTask;
import simblock.task.BlockMessageTask;

import static simblock.settings.SimulationConfiguration.BLOCK_SIZE;

public class AlgorandMsgTask extends BlockMessageTask {

    private AlgorandMsgType type;
    private int round, period, step;
    private Block block;

    /**
     * Instantiates a new Vote message task.
     *
     * @param from   sender node
     * @param to     receiver node
     * @param t      type of vote
     * @param round  round vote refers to
     * @param period period vote refers to
     * @param step   step vote refers to
     */
    public AlgorandMsgTask(Node from, Node to, AlgorandMsgType t, int round, int period, int step, Block block, long delay) {
        super(from, to, block, delay);
        this.type = t;
        this.period = period;
        this.round = round;
        this.step = step;
        this.block = block;
    }

    public AlgorandMsgType getType() {  return type; }

    public int getRound() { return round; }

    public int getPeriod() { return period; }

    public int getStep() { return step; }

    public Block getBlock() { return this.block; }
}

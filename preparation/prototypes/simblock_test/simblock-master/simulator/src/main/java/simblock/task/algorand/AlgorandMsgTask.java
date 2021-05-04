package simblock.task.algorand;

import simblock.block.Block;
import simblock.node.Node;
import simblock.task.AbstractMessageTask;
import simblock.task.BlockMessageTask;

import static simblock.settings.SimulationConfiguration.BLOCK_SIZE;
import static simblock.simulator.Main.OUT_JSON_FILE;
import static simblock.simulator.Timer.getCurrentTime;

public class AlgorandMsgTask extends BlockMessageTask {

    private AlgorandMsgType type;
    private int round, period, step;
    private Block block;
    private Node voteFrom;

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
    public AlgorandMsgTask(Node from, Node to, AlgorandMsgType t, int round, int period, int step, Block block, long delay, Node voteFrom) {
        super(from, to, block, delay);
        this.type = t;
        this.period = period;
        this.round = round;
        this.step = step;
        this.block = block;
        this.voteFrom = voteFrom;
    }

    public AlgorandMsgType getType() {  return type; }

    public int getRound() { return round; }

    public int getPeriod() { return period; }

    public int getStep() { return step; }

    public Block getBlock() { return this.block; }

    public Node getVoteFrom() { return this.voteFrom; }

    /**
     * Overwriting the run method with the purpose of using a different event, so that it doesn't update the
     * state of a node's chain in the visualizer
     */
    @Override
    public void run() {
        String sType = "";
        switch(getType()) {
            case PROPOSAL:
                sType = "PROPOSAL";
                break;
            case SOFTVOTE:
                sType = "SOFTVOTE";
                break;
            case CERTVOTE:
                sType = "CERTVOTE";
                break;
            case NEXTVOTE:
                sType = "NEXTVOTE";
                break;
            default:
                break;

        }
        OUT_JSON_FILE.print("{");
        OUT_JSON_FILE.print("\"kind\":\"flow-message\",");
        OUT_JSON_FILE.print("\"content\":{");
        OUT_JSON_FILE.print("\"transmission-timestamp\":" + (getCurrentTime() - super.getInterval()) + ",");
        OUT_JSON_FILE.print("\"reception-timestamp\":" + getCurrentTime() + ",");
        OUT_JSON_FILE.print("\"begin-node-id\":" + getFrom().getNodeID() + ",");
        OUT_JSON_FILE.print("\"end-node-id\":" + getTo().getNodeID() + ",");
        OUT_JSON_FILE.print("\"msg-type\":\"" + sType + "\",");
        OUT_JSON_FILE.print("\"msg-creator\":\"" + getVoteFrom().getNodeID() + "\",");
        OUT_JSON_FILE.print("\"block-id\":" + (this.block == null ? -1 : block.getId()));
        OUT_JSON_FILE.print("}");
        OUT_JSON_FILE.print("},");
        OUT_JSON_FILE.flush();

        super.getTo().receiveMessage(this);
    }
}

package simblock.node;

import simblock.node.consensus.AbstractConsensusAlgo;
import simblock.node.consensus.AlgorandConsensus;
import simblock.task.AbstractMessageTask;
import simblock.task.AbstractMintingTask;
import simblock.task.algorand.AlgorandIncStepTask;
import simblock.task.algorand.AlgorandMsgTask;

import static simblock.simulator.Timer.putTask;

public class AlgorandNode extends Node {

    /**
     * Instantiates a new Algorand Node.
     *
     * @param nodeID            the node id
     * @param numConnection     the number of connections a node can have
     * @param region            the region
     * @param miningPower       the mining power
     * @param routingTableName  the routing table name
     * @param consensusAlgoName the consensus algorithm name
     * @param useCBR            whether the node uses compact block relay
     * @param isChurnNode       whether the node causes churn
     */
    public AlgorandNode(int nodeID, int numConnection, int region, long miningPower, String routingTableName,
                        String consensusAlgoName, boolean useCBR, boolean isChurnNode) {
        super(nodeID, numConnection, region, miningPower, routingTableName, consensusAlgoName, useCBR, isChurnNode);
    }


    @Override
    public void minting() {
        // TODO abstraction for cryptographic sortition
    }

    /**
     * Receive message.
     *
     * @param message the message
     */
    @Override
    public void receiveMessage(AbstractMessageTask message) {
        // TODO(miguel) Always discard messages that do not extend the last agreed upon block in the chain
        if(message instanceof AlgorandIncStepTask) {
            ((AlgorandConsensus)this.getConsensusAlgo()).runStep((AlgorandIncStepTask)message);
        }
        else if(message instanceof AlgorandMsgTask) {
            ((AlgorandConsensus)this.getConsensusAlgo()).processMessage((AlgorandMsgTask)message);
        }
        else {
            super.receiveMessage(message);
        }
    }

}

package simblock.node.consensus;

import simblock.block.Block;
import simblock.node.Node;
import simblock.task.AbstractMintingTask;

public class AlgorandConsensus extends AbstractConsensusAlgo {

    public AlgorandConsensus(Node selfNode) {
        super(selfNode);
    }

    @Override
    public AbstractMintingTask minting() {
        return null;
    }

    @Override
    public boolean isReceivedBlockValid(Block receivedBlock, Block currentBlock) {
        return false;
    }

    @Override
    public Block genesisBlock() {
        return null;
    }
}

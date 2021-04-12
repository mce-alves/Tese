package simblock.node.consensus;

import simblock.block.Block;
import simblock.block.SamplePoSBlock;
import simblock.node.Node;
import simblock.settings.SimulationConfiguration;
import simblock.task.AbstractMessageTask;
import simblock.task.AbstractMintingTask;
import simblock.task.algorand.AlgorandBaseMsgTask;
import simblock.task.algorand.AlgorandMsgType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static simblock.simulator.Timer.putTask;

/**
 * Notes:
 * -> In the node, only read messages that should be processed in the current round+period+step combination
 * otherwise, keep them in the message queue.
 * -> Always discard messages that do not extend the last agreed upon block in the chain
 * -> https://people.csail.mit.edu/nickolai/papers/gilad-algorand-eprint.pdf contains in pages 11 and 12 the
 *    information needed to define the constants T and REQUIRED_VOTES
 */

public class AlgorandConsensus extends AbstractConsensusAlgo {

    // TODO(miguel) check the values for the constants
    // The constant used in computing the deadlines for each step in the protocol (milliseconds)
    private static final long LAMBDA = 100;
    // Committee size for each step
    private static final long T = 20;
    // Voting threshold (usually 2/3 majority)
    private static final long REQUIRED_VOTES = ((2/3) * T) + 1;

    /**
     * round: node's current round
     * period: node's current period
     * step: node's current step
     *
     * proposals: proposals received for the given round
     * softvotes: softvotes received for the current round
     * certvotes: certvotes received for the current round
     * nextvotes: nextvotes received for the current round
     *
     * timer: node's local timer
     * deadline: node's next time deadline
     * period_start: node's time when current period started
     *
     * canAdvanceRound: if the last round of the protocol has been completed
     */

    private int round, period, step;
    private long timer, deadline, period_start;
    private ArrayList<AlgorandBaseMsgTask> mQueue, proposals, softvotes, certvotes, nextvotes;
    private Block startingValue;
    private ArrayList<Block> blocks;
    private boolean canAdvanceRound;

    public AlgorandConsensus(Node selfNode) {
        super(selfNode);
        this.round = 1;
        this.period = 1;
        this.step = 1;
        this.timer = 0;
        this.deadline = 0; // TODO(miguel) check if starts at 0, or 0+lambda
        this.period_start = 0;
        this.mQueue = new ArrayList<>();
        this.proposals = new ArrayList<>();
        this.softvotes = new ArrayList<>();
        this.certvotes = new ArrayList<>();
        this.nextvotes = new ArrayList<>();
        this.startingValue = null;
        this.blocks = new ArrayList<>();
        this.canAdvanceRound = false;
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
        return SamplePoSBlock.genesisBlock(this.getSelfNode());
    }

    // Step 1 of the Agreement Protocol
    public void valueProposal() {
        if(step == 1) {
            if(tryAdvanceRound()) {
                // new round, meaning period = 1, so node is free to propose anything (if he is selected to propose)
                // TODO(miguel) requires the sortition function to be implemented
            }
            else {
                // TODO(miguel) make sure only the votes from the previous period are being counted
                // TODO(miguel) maybe create a new list, for votes from previous period, and update them when changing periods
                // TODO(miguel) no need to keep all periods in memory, only the current and previous periods
                Block mostVotedBlock = mostVoted(countVotes(nextvotes));
                if(mostVotedBlock != null) {
                    // if there was a block with more than REQUIRED_VOTES in the previous period, propose it
                    startingValue = mostVotedBlock;
                }

            }
            // the node proposes its starting value
            // if he was selected to propose and this is period=1, then starting value will be the node's created block
            // if the node wasn't selected to propose and it is period=1, then starting value will be null (empty)
            // otherwise if period>1, the starting value will be V such as V received REQUIRED_VOTES in previous period
            broadcastProtocolMessage(AlgorandMsgType.PROPOSAL, round, period, step, startingValue);
        }
    }

    // Step 2 of the Agreement Protocol
    public void filteringStep() {

    }

    // Step 3 of the Agreement Protocol
    public void certifyingStep() {

    }

    // Step 4 of the Agreement Protocol
    public void finishingStepOne() {

    }

    // Step 5 of the Agreement Protocol
    public void finishingStepTwo() {

    }

    // The halting condition for the Agreement Protocol
    public boolean haltingCondition() {
        return true;
    }

    /* Auxiliary functions */

    // Advance to the next round, if possible
    private boolean tryAdvanceRound() {
        if(round == 1 || canAdvanceRound) {
            round += 1;
            canAdvanceRound = false;
            cleanData();
            return true; // success
        }
        return false; // failed
    }

    // Clean the data from the previous round
    private void cleanData() {
        this.mQueue.clear();
        this.proposals.clear();
        this.softvotes.clear();
        this.certvotes.clear();
        this.nextvotes.clear();
        this.startingValue = null;
        this.blocks.clear();
    }

    // Create a frequency map for block votes. <Integer, Long> is the pair <BlockId, NumberOfVotes>
    private Map<Integer, Long> countVotes(ArrayList<AlgorandBaseMsgTask> votes) {
        Integer[] blockIds = new Integer[votes.size()];
        // create an array with all the block ids contained in the votes
        for(AlgorandBaseMsgTask vote : votes) {
            blockIds[blockIds.length] = vote.getBlock().getId();
        }

        Map<Integer, Long> counts = Arrays.stream(blockIds)
                .collect(Collectors.groupingBy(x -> x, Collectors.counting()));

        return counts;
    }

    // Obtain the most voted block in the frequency map, if it obtained at least REQUIRED_VOTES
    private Block mostVoted(Map<Integer, Long> voteFreq) {
        int mostVotedId = - 1;
        long mostVotes = 0;
        // find the most voted blockId
        for(Map.Entry<Integer, Long> entry : voteFreq.entrySet()) {
            if(entry.getValue() > mostVotes) {
                mostVotedId = entry.getKey();
                mostVotes = entry.getValue();
            }
        }

        // if it obtained more than the required votes, return the block with the corresponding id
        if(mostVotes >= REQUIRED_VOTES) {
            for(Block b : blocks) {
                if(b.getId() == mostVotedId) {
                    return b;
                }
            }
        }

        return null;
    }

    private void broadcastProtocolMessage(AlgorandMsgType type, int round, int period, int step, Block proposal) {
        for (Node to : getSelfNode().getRoutingTable().getNeighbors()) {
            AbstractMessageTask task = new AlgorandBaseMsgTask(getSelfNode(), to, type, round, period, step, proposal);
            // TODO(miguel) since blocks are being exchanged, should extend the behavior of BlockMessageTask, instead of the abstract, otherwise, latency delays won't consider the size of the blocks
            putTask(task);
        }
    }

}

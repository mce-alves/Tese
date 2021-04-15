package simblock.node.consensus;

import com.sun.tools.javac.util.Pair;
import simblock.block.Block;
import simblock.block.SamplePoSBlock;
import simblock.node.Node;
import simblock.task.AbstractMintingTask;
import simblock.task.algorand.AlgorandIncStepTask;
import simblock.task.algorand.AlgorandMsgTask;
import simblock.task.algorand.AlgorandMsgType;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static simblock.settings.SimulationConfiguration.BLOCK_SIZE;
import static simblock.simulator.Network.getBandwidth;
import static simblock.simulator.Timer.putTask;

/**
 * Notes:
 * -> (NODE CLASS) Only read messages that should be processed in the current round+period+step combination
 * otherwise, keep them in the message queue
 * -> (NODE CLASS) Always discard messages that do not extend the last agreed upon block in the chain
 */

// TODO(miguel) change steps in events from integers to enums

public class AlgorandConsensus extends AbstractConsensusAlgo {

    // TODO(miguel) check the values for the constants (pages 11 and 12)
    //  https://people.csail.mit.edu/nickolai/papers/gilad-algorand-eprint.pdf
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
     * prevsoftvotes: softvotes received for the previous round
     * prevcertvotes: certvotes received for the previous round
     * prevnextvotes: nextvotes received for the previous round
     *
     * timer: node's local timer
     * deadline: node's next time deadline
     * period_start: node's time when current period started
     *
     * certVoted: if the node has certVoted a block in the current round+period
     * canAdvanceRound: if the last round of the protocol has been completed
     */

    private int round, period, step;
    private long deadline, period_start;
    private ArrayList<AlgorandMsgTask> proposals, softvotes, certvotes, nextvotes, prevsoftvotes, prevcertvotes, prevnextvotes;
    private Block startingValue;
    private Pair<Boolean, Block> certVoted;
    private ArrayList<Block> blocks; //TODO(miguel) stop using this (duplicating information unnecessarily)
    private boolean canAdvanceRound;

    public AlgorandConsensus(Node selfNode) {
        super(selfNode);
        this.round = 1;
        this.period = 1;
        this.step = 1;
        this.deadline = 0;
        this.period_start = 0;
        this.proposals = new ArrayList<>();
        this.softvotes = new ArrayList<>();
        this.certvotes = new ArrayList<>();
        this.nextvotes = new ArrayList<>();
        this.prevsoftvotes = new ArrayList<>();
        this.prevcertvotes = new ArrayList<>();
        this.prevnextvotes = new ArrayList<>();
        this.startingValue = null;
        this.certVoted = new Pair(false, null);
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
                // TODO(miguel) otherwise, it proposes nothing?
                // TODO(miguel) requires the sortition function to be implemented
                broadcastProtocolMessage(AlgorandMsgType.PROPOSAL, round, period, step, startingValue);
            }
            else {
                Pair<Boolean, Block> mostNextVotedBlock = mostVoted(countVotes(prevnextvotes));
                if(mostNextVotedBlock.fst && mostNextVotedBlock.snd != null) {
                    // if there was a block with more than REQUIRED_VOTES in the previous period, propose it
                    broadcastProtocolMessage(AlgorandMsgType.PROPOSAL, round, period, step, mostNextVotedBlock.snd);
                }
            }
            // if it was selected to propose and this is period=1, then proposal will be the node's created block
            // if the node wasn't selected to propose and it is period=1, then proposal will be its starting value
            // otherwise if period>1, the starting value will be V such that V received REQUIRED_VOTES in previous period

            putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 2));
        }
    }

    // Step 2 of the Agreement Protocol
    public void filteringStep() throws NoSuchAlgorithmException {
        if(step == 2 && !canAdvanceRound) {
            Pair<Boolean, Block> mostNextVotedBlock = mostVoted(countVotes(prevnextvotes));
            if(period >= 2 && mostNextVotedBlock.fst && mostNextVotedBlock.snd != null) {
                // if period >= 2 and there was a non-empty block with more than REQUIRED_VOTES
                broadcastProtocolMessage(AlgorandMsgType.SOFTVOTE, round, period, step, mostNextVotedBlock.snd);
            }
            else {
                // identify the leader for this period
                // the leader is the node whose credential's hash is smallest
                broadcastProtocolMessage(AlgorandMsgType.SOFTVOTE, round, period, step, findLeaderProposal());
            }
            putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 3));
        }
    }

    // Step 3 of the Agreement Protocol
    public void certifyingStep() {
        if(step == 3 && !canAdvanceRound) {
            Pair<Boolean, Block> mostSoftVotedBlock = mostVoted(countVotes(softvotes));
            if(mostSoftVotedBlock.fst && mostSoftVotedBlock.snd != null) {
                // if there was a block with more than REQUIRED_VOTES softvotes in the current period, certvote for it
                certVoted = new Pair<>(true, mostSoftVotedBlock.snd);
                broadcastProtocolMessage(AlgorandMsgType.CERTVOTE, round, period, step, certVoted.snd);
            }
            putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 4));
        }
    }

    // Step 4 of the Agreement Protocol
    public void finishingStepOne() {
        if(step == 4 && !canAdvanceRound) {
            Pair<Boolean, Block> mostNextVotedBlock = mostVoted(countVotes(nextvotes));
            if(certVoted.fst) {
                // if the node has certvoted for a block in this period, nextvote for that same block
                broadcastProtocolMessage(AlgorandMsgType.NEXTVOTE, round, period, step, certVoted.snd);
            }
            else if(period >=2 && mostNextVotedBlock.fst && mostNextVotedBlock.snd == null) {
                // if the node has seen a majority of nextvotes for the empty block, nextvote the empty block
                broadcastProtocolMessage(AlgorandMsgType.NEXTVOTE, round, period, step, null);
            }
            else {
                // otherwise, the node sends a nextvote with its starting value
                broadcastProtocolMessage(AlgorandMsgType.NEXTVOTE, round, period, step, startingValue);
            }
            putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 5));
        }
    }

    // Step 5 of the Agreement Protocol
    public void finishingStepTwo() {
        if(step == 5 && !canAdvanceRound) {
            Pair<Boolean, Block> mostSoftVotedBlock = mostVoted(countVotes(softvotes));
            Pair<Boolean, Block> mostNextVotedBlock = mostVoted(countVotes(prevnextvotes));
            if(mostSoftVotedBlock.fst && mostSoftVotedBlock.snd != null) {
                // if the most voted block that received REQUIRED_VOTES is not empty, nextvote it
                broadcastProtocolMessage(AlgorandMsgType.NEXTVOTE, round, period, step, mostSoftVotedBlock.snd);
            }
            else if(period >= 2 && mostNextVotedBlock.fst && mostNextVotedBlock.snd == null && !certVoted.fst) {
                // if the node hasn't certvoted and sees a majority of softvotes for empty block, nextvote empty block
                broadcastProtocolMessage(AlgorandMsgType.NEXTVOTE, round, period, step, null);
            }
            // check if node can finish the round
            if(haltingCondition()) {
                putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 1));
            }
            // check if node can finish the period
            Pair<Boolean, Block> nextVotedBlock = mostVoted(countVotes(nextvotes));
            if(nextVotedBlock.fst) {
                startingValue = nextVotedBlock.snd;
                nextPeriod();
                putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 1));
            }
            putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 5));
        }
    }

    // The halting condition for the Agreement Protocol
    // Called when the node emits or receives a certvote
    public boolean haltingCondition() {
        Pair<Boolean, Block> mostCertVotedBlock = mostVoted(countVotes(certvotes));
        if(mostCertVotedBlock.fst) {
            // if there is a block with more than REQUIRED_VOTES certvotes, then consensus was reached
            // and that block can be added to the chain
            getSelfNode().addToChain(mostCertVotedBlock.snd);
            canAdvanceRound = true;
            return true;
        }
        return false;
    }




    /** Auxiliary functions **/

    // Store a received message in the correct list, if it can be processed at the moment (not a "future" message)
    // This function will be called in the Node's "receiveMessage" function
    private boolean tryProcessMessage(AlgorandMsgTask msg) {
        if(msg.getPeriod() > period) {
            // message is for a future period, so should not be processed yet
            return false;
        }
        switch(msg.getType()) {
            case CERTVOTE:
                addNoDuplicate(msg, msg.getPeriod() == period-1 ? prevcertvotes : certvotes);
                break;
            case NEXTVOTE:
                addNoDuplicate(msg, msg.getPeriod() == period-1 ? prevnextvotes : nextvotes);
                Pair<Boolean, Block> mostNextVotedBlock = mostVoted(countVotes(nextvotes));
                if(mostNextVotedBlock.fst) {
                    // If the node sees more than REQUIRED_VOTES for a block B in the current period, it starts the next
                    // period and uses B as its starting value
                    startingValue = mostNextVotedBlock.snd;
                    nextPeriod();
                }
                break;
            case SOFTVOTE:
                addNoDuplicate(msg, msg.getPeriod() == period-1 ? prevsoftvotes : softvotes);
                break;
            case PROPOSAL:
                addNoDuplicate(msg, proposals);
                break;
            default:
                break;
        }
        return true;
    }

    // Add a message to a list, if it doesn't already contain a message from the same node
    private void addNoDuplicate(AlgorandMsgTask msg, ArrayList<AlgorandMsgTask> list) {
        for(AlgorandMsgTask in : list) {
            if(in.getFrom().getNodeID() == msg.getFrom().getNodeID()) {
                // do nothing if already received from that node
                return;
            }
        }
        // if haven't received, add msg to list
        list.add(msg);
    }


    // Advance to the next round, if possible
    private boolean tryAdvanceRound() {
        // round == 1 is because there is no need for agreement for the genesis block
        if(round == 1 || canAdvanceRound) {
            round += 1;
            period = 1;
            step = 1;
            canAdvanceRound = false;
            cleanData();
            return true; // success
        }
        return false; // failed
    }

    // Clean the data from the previous round
    private void cleanData() {
        this.proposals.clear();
        this.softvotes.clear();
        this.certvotes.clear();
        this.nextvotes.clear();
        this.prevsoftvotes.clear();
        this.prevnextvotes.clear();
        this.prevcertvotes.clear();
        this.startingValue = null;
        this.blocks.clear();
        this.certVoted = new Pair(false, null);
    }

    // Create a frequency map for block votes. <Integer, Long> is the pair <BlockId, NumberOfVotes>
    // The arg is the list of votes of type Pair <node_id, block_hash>
    private Map<Integer, Long> countVotes(ArrayList<AlgorandMsgTask> votes) {
        Integer[] blockIds = new Integer[votes.size()];
        // create an array with all the block ids contained in the votes
        int i = 0;
        for(AlgorandMsgTask vote : votes) {
            blockIds[i] = vote.getBlock().getId();
            i++;
        }

        return Arrays.stream(blockIds)
                .collect(Collectors.groupingBy(x -> x, Collectors.counting()));
    }

    /** Obtain the most voted block in the frequency map, if it obtained at least REQUIRED_VOTES
     *
     * Pair is used to differentiate between the <true, null> (meaning the most voted block was the empty block)
     * and <false, null> (no block was found that contained the necessary votes)
     */
    private Pair<Boolean, Block> mostVoted(Map<Integer, Long> voteFreq) {
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
                    return new Pair<>(true, b);
                }
            }
        }

        return new Pair<>(false, null);
    }

    // Create a message receive task for every neighbor node
    private void broadcastProtocolMessage(AlgorandMsgType type, int round, int period, int step, Block proposal) {
        for (Node to : getSelfNode().getRoutingTable().getNeighbors()) {
            long bandwidth = getBandwidth(getSelfNode().getRegion(), to.getRegion()); // copied from Node "sendNextBlockMessage"
            long delay = BLOCK_SIZE * 8 / (bandwidth / 1000) + 2; // copied from Node "sendNextBlockMessage"
            putTask(new AlgorandMsgTask(getSelfNode(), to, type, round, period, step, proposal, delay));
        }
        // also stores its own message, regardless of whether it is a vote or proposal
        tryProcessMessage(new AlgorandMsgTask(getSelfNode(), getSelfNode(), type, round, period, step, proposal, 0));
    }

    // Increment period updating corresponding state
    private void nextPeriod() {
        period += 1; step = 1; certVoted = new Pair<>(false, null);
        prevcertvotes.clear(); prevnextvotes.clear(); prevsoftvotes.clear();
        prevcertvotes = (ArrayList<AlgorandMsgTask>) certvotes.clone();
        prevnextvotes = (ArrayList<AlgorandMsgTask>) nextvotes.clone();
        prevsoftvotes = (ArrayList<AlgorandMsgTask>) softvotes.clone();
        certvotes.clear(); nextvotes.clear(); softvotes.clear();
        proposals.clear();
    }

    // From the received proposals, identify the leader and return the block proposed by him
    private Block findLeaderProposal() throws NoSuchAlgorithmException {
        // the leader of period p is the node J that possesses the credentials for the round+period with smallest hash
        // for simplification, we will consider the credentials to be the round+period appended to the node's ID
        Block fromLeader = null; String minHash = ""; boolean firstIteration = true;
        String suffix = Integer.toString(round).concat(Integer.toString(period));
        for(AlgorandMsgTask proposal : proposals) {
            String credential = Integer.toString(proposal.getFrom().getNodeID()) + suffix;

            // https://stackoverflow.com/a/3103722 -> calculating an hash in Java
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(credential.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            String hash = String.format("%064x", new BigInteger(1, digest));

            if(firstIteration || hash.compareTo(minHash) < 0) {
                firstIteration = false;
                minHash = hash;
                fromLeader = proposal.getBlock();
            }
        }

        return fromLeader;
    }

}

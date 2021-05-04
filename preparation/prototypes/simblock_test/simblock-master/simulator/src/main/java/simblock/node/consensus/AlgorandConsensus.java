package simblock.node.consensus;


import simblock.auxiliary.MyLogger;
import simblock.auxiliary.Pair;
import simblock.block.Block;
import simblock.block.SamplePoSBlock;
import simblock.node.Node;
import simblock.simulator.Main;
import simblock.simulator.statistics.AlgorandStatistics;
import simblock.task.SampleStakingTask;
import simblock.task.algorand.AlgorandIncStepTask;
import simblock.task.algorand.AlgorandMsgTask;
import simblock.task.algorand.AlgorandMsgType;

import javax.management.relation.RelationNotFoundException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static simblock.settings.SimulationConfiguration.BLOCK_SIZE;
import static simblock.settings.SimulationConfiguration.NUM_OF_NODES;
import static simblock.simulator.Main.OUT_JSON_FILE;
import static simblock.simulator.Network.getBandwidth;
import static simblock.simulator.Timer.getCurrentTime;
import static simblock.simulator.Timer.putTask;

public class AlgorandConsensus extends AbstractConsensusAlgo {

    // TODO(miguel) check what are appropriate values for the constants (pages 11 and 12)
    //  https://people.csail.mit.edu/nickolai/papers/gilad-algorand-eprint.pdf
    // The constant used in computing the deadlines for each step in the protocol (milliseconds)
    public static final long LAMBDA = 1000;
    // Committee size for each step
    public static final int T = 20;
    // Voting threshold (usually 2/3 majority)
    public static final int REQUIRED_VOTES = 15;

    /**
     * round: node's current round
     * period: node's current period
     * step: node's current step
     *
     * mQueue: store messages for future periods to be processed later
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
    private ArrayList<AlgorandMsgTask> mQueue, proposals, softvotes, certvotes, nextvotes, prevsoftvotes, prevnextvotes;
    private Block startingValue;
    private Pair<Boolean, Block> certVoted;
    private ArrayList<Block> blocks;

    public AlgorandConsensus(Node selfNode) {
        super(selfNode);
        this.round = 1;
        this.period = 1;
        this.step = 1;
        this.proposals = new ArrayList<>();
        this.softvotes = new ArrayList<>();
        this.certvotes = new ArrayList<>();
        this.nextvotes = new ArrayList<>();
        this.mQueue = new ArrayList<>();
        this.prevsoftvotes = new ArrayList<>();
        this.prevnextvotes = new ArrayList<>();
        this.startingValue = null;
        this.certVoted = new Pair(false, null);
        this.blocks = new ArrayList<>();
    }

    @Override
    public SampleStakingTask minting() {
        return null; //TODO
    }

    @Override
    public boolean isReceivedBlockValid(Block receivedBlock, Block currentBlock) {
        // Copied FROM SampleProofOfStake class
        if (!(receivedBlock instanceof SamplePoSBlock)) {
            return false;
        }
        SamplePoSBlock recPoSBlock = (SamplePoSBlock) receivedBlock;
        SamplePoSBlock currPoSBlock = (SamplePoSBlock) currentBlock;
        int receivedBlockHeight = receivedBlock.getHeight();
        SamplePoSBlock receivedBlockParent = receivedBlockHeight == 0 ? null :
                (SamplePoSBlock) receivedBlock.getBlockWithHeight(receivedBlockHeight - 1);

        return (
                receivedBlockHeight == 0 ||
                        recPoSBlock.getDifficulty().compareTo(receivedBlockParent.getNextDifficulty()) >= 0
        ) && (
                currentBlock == null ||
                        recPoSBlock.getTotalDifficulty().compareTo(currPoSBlock.getTotalDifficulty()) > 0
        );
    }

    @Override
    public Block genesisBlock() {
        // FROM SampleProofOfStake
        return SamplePoSBlock.genesisBlock(this.getSelfNode());
    }


    // Step 1 of the Agreement Protocol
    public void valueProposal() {
        if(step == 1) {
            if(period == 1) {
                // if period = 1, then node is free to propose anything (if he is selected to propose)
                if(selectedBySortition(getSelfNode().getNodeID())) {
                    log("Selected to propose.");
                    createAndProposeBlock();
                }
            }
            else {
                Pair<Boolean, Block> mostNextVotedBlock = mostVoted(countVotes(prevnextvotes));
                if(mostNextVotedBlock.first && mostNextVotedBlock.second != null) {
                    // if there was a non-empty block with more than REQUIRED_VOTES in the previous period, propose it
                    if(inCommitee(getSelfNode().getNodeID())) {
                        log("Proposing non-empty with majority votes from previous period. Block id="+mostNextVotedBlock.second.getId());
                        broadcastProtocolMessage(AlgorandMsgType.PROPOSAL, round, period, step, mostNextVotedBlock.second);
                    }
                }
                else {
                    // otherwise, re-check if selected by sortition, and try to create a block to propose
                    if(selectedBySortition(getSelfNode().getNodeID())) {
                        log("Selected to propose.");
                        createAndProposeBlock();
                    }

                }
            }
            // if it was selected to propose and this is period=1, then proposal will be the node's created block
            // if the node wasn't selected to propose and it is period=1, then proposal will be its starting value
            // otherwise if period>1, the starting value will be V such that V received REQUIRED_VOTES in previous period
            // if such a value doesn't exist, the node will recheck if it is selected to propose, and try to create a block

            putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 2));
            step++;
        }
    }

    // Step 2 of the Agreement Protocol
    public void filteringStep() throws NoSuchAlgorithmException {
        if(step == 2) {
            if(inCommitee(getSelfNode().getNodeID())) {
                Pair<Boolean, Block> mostNextVotedBlock = mostVoted(countVotes(prevnextvotes));
                if(period >= 2 && mostNextVotedBlock.first && mostNextVotedBlock.second != null) {
                    // if period >= 2 and there was a non-empty block with more than REQUIRED_VOTES
                    log("Soft Voting for the block with majority Next Votes from previous period. Block id="+mostNextVotedBlock.second.getId());
                    broadcastProtocolMessage(AlgorandMsgType.SOFTVOTE, round, period, step, mostNextVotedBlock.second);
                }
                else {
                    // identify the leader for this period
                    // the leader is the node whose credential's hash is smallest, in case of multiple proposers
                    Block leaderProposal = findLeaderProposal();
                    if(leaderProposal != null) {
                        log("Soft Voting for the block proposed by the identified leader. Block id="+leaderProposal.getId()+". Leader id="+leaderProposal.getMinter().getNodeID());
                    }
                    else {
                        // this node has not seen any proposals
                        log("Soft Voting for the empty block, since no leader proposals were found.");
                    }
                    broadcastProtocolMessage(AlgorandMsgType.SOFTVOTE, round, period, step, leaderProposal);
                }
            }
            putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 3));
            step++;
        }
    }

    // Step 3 of the Agreement Protocol
    public void certifyingStep() {
        if(step == 3) {
            if(inCommitee(getSelfNode().getNodeID())) {
                Pair<Boolean, Block> mostSoftVotedBlock = mostVoted(countVotes(softvotes));
                if(mostSoftVotedBlock.first && mostSoftVotedBlock.second != null) {
                    // if there was a block with more than REQUIRED_VOTES softvotes in the current period, certvote for it
                    certVoted = new Pair<>(true, mostSoftVotedBlock.second);
                    log("Cert Voting for the block that received majority Soft Votes. Block id="+certVoted.second.getId());
                    broadcastProtocolMessage(AlgorandMsgType.CERTVOTE, round, period, step, certVoted.second);
                }
                else {
                    log("Did not see any block with majority of Soft Votes, so not Cert Voting for any block.");
                }
            }
            putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 4));
            step++;
        }
    }

    // Step 4 of the Agreement Protocol
    public void finishingStepOne() {
        if(step == 4) {
            if(inCommitee(getSelfNode().getNodeID())) {
                Pair<Boolean, Block> mostNextVotedBlock = mostVoted(countVotes(nextvotes));
                if(certVoted.first) {
                    // if the node has certvoted for a block in this period, nextvote for that same block
                    log("Next Voting for the block that I Cert Voted. Block id="+certVoted.second.getId());
                    broadcastProtocolMessage(AlgorandMsgType.NEXTVOTE, round, period, step, certVoted.second);
                }
                else if(period >=2 && mostNextVotedBlock.first && mostNextVotedBlock.second == null) {
                    // if the node has seen a majority of nextvotes for the empty block, nextvote the empty block
                    log("Next Voting for the empty block, since I have seen a majority of Next Votes for it.");
                    broadcastProtocolMessage(AlgorandMsgType.NEXTVOTE, round, period, step, null);
                }
                else {
                    // otherwise, the node sends a nextvote with its starting value
                    log("Next Voting my starting value.");
                    broadcastProtocolMessage(AlgorandMsgType.NEXTVOTE, round, period, step, startingValue);
                }
            }
            putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 5));
            step++;
        }
    }

    // Step 5 of the Agreement Protocol
    public void finishingStepTwo() {
        if(step == 5) {
            if(inCommitee(getSelfNode().getNodeID())) {
                Pair<Boolean, Block> mostSoftVotedBlock = mostVoted(countVotes(softvotes));
                Pair<Boolean, Block> mostNextVotedBlock = mostVoted(countVotes(prevnextvotes));
                if(mostSoftVotedBlock.first && mostSoftVotedBlock.second != null) {
                    // if the most voted block that received REQUIRED_VOTES is not empty, nextvote it
                    log("Next Voting for the non-empty block that received the most Soft Votes. Block id="+mostSoftVotedBlock.second.getId());
                    broadcastProtocolMessage(AlgorandMsgType.NEXTVOTE, round, period, step, mostSoftVotedBlock.second);
                }
                else if(period >= 2 && mostNextVotedBlock.first && mostNextVotedBlock.second == null && !certVoted.first) {
                    // if the node hasn't certvoted and sees a majority of softvotes for empty block, nextvote empty block
                    log("Next Voting for the empty block since I have not Cert Voted a block and didn't see a non-empty block with majority of Soft Votes.");
                    broadcastProtocolMessage(AlgorandMsgType.NEXTVOTE, round, period, step, null);
                }
            }
            // check if node can finish the round
            if(haltingCondition()) {
                putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 1));
                return;
            }
            // check if node can finish the period
            Pair<Boolean, Block> nextVotedBlock = mostVoted(countVotes(nextvotes));
            if(nextVotedBlock.first) {
                startingValue = nextVotedBlock.second;
                nextPeriod();
                putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 1));
                return;
            }
            putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 5));
        }
    }

    // The halting condition for the Agreement Protocol
    // Called when the node emits or receives a certvote
    // Upon success advances the state to the next round
    public boolean haltingCondition() {
        Pair<Boolean, Block> mostCertVotedBlock = mostVoted(countVotes(certvotes));
        if(mostCertVotedBlock.first && mostCertVotedBlock.second != null) {
            log("Reached halting condition. Adding block with id="+mostCertVotedBlock.second.getId()+" to chain. Advancing round.");
            // if there is a block with more than REQUIRED_VOTES certvotes, then consensus was reached
            // and that block can be added to the chain
            getSelfNode().addToChain(mostCertVotedBlock.second);
            AlgorandStatistics.getInstance().consensusReached(getSelfNode().getNodeID(), getCurrentTime(), round);
            advanceRound();
            return true;
        }
        return false;
    }




    /** Auxiliary functions **/

    // Store a received message in the correct list, if it can be processed at the moment (otherwise store in queue)
    // This function will be called in the Node's "receiveMessage" function
    public void processMessage(AlgorandMsgTask msg) {
        if(msg.getRound() < round || msg.getPeriod() < period - 1) {
            // ignore late messages (shouldn't happen, but just to be safe)
            return;
        }
        if(msg.getPeriod() > period || msg.getRound() > round) {
            // message is for a future period/round, so should not be processed yet
            mQueue.add(msg);
            return;
        }
        boolean isDuplicate = false;
        switch(msg.getType()) {
            case CERTVOTE:
                isDuplicate = addNoDuplicate(msg, certvotes);
                if(haltingCondition()) {
                    putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 1));
                }
                break;
            case NEXTVOTE:
                isDuplicate = addNoDuplicate(msg, msg.getPeriod() == period-1 ? prevnextvotes : nextvotes);
                Pair<Boolean, Block> mostNextVotedBlock = mostVoted(countVotes(nextvotes));
                if(mostNextVotedBlock.first) {
                    // If the node sees more than REQUIRED_VOTES for a block B in the current period, it starts the next
                    // period and uses B as its starting value
                    log("Received majority of Next Votes");
                    startingValue = mostNextVotedBlock.second;
                    nextPeriod();
                    putTask(new AlgorandIncStepTask(getSelfNode(), 2*LAMBDA, 1));
                }
                break;
            case SOFTVOTE:
                isDuplicate = addNoDuplicate(msg, msg.getPeriod() == period-1 ? prevsoftvotes : softvotes);
                break;
            case PROPOSAL:
                isDuplicate = addNoDuplicate(msg, proposals);
                break;
            default:
                break;
        }
        if(!isDuplicate) {
            // register the seen block, if it has not yet been registered
            registerBlock(msg.getBlock());
            // propagate new messages to neighbors
            propagateMessage(msg);
        }
    }

    private void createAndProposeBlock() {
        if(round == 1) {
            // if chosen to propose in the first round, then propose the genesis block
            startingValue = genesisBlock();
            log("Proposing genesis block.");
            printCreateBlock(startingValue);
            broadcastProtocolMessage(AlgorandMsgType.PROPOSAL, round, period, step, startingValue);
        }
        else {
            // create a block that extends the current head of the chain
            // coin flip to abstract if it is able to create a block at this time
            if(Main.random.nextBoolean()) {
                SamplePoSBlock parent = (SamplePoSBlock) getSelfNode().getBlock();
                startingValue = new SamplePoSBlock(parent, getSelfNode(), getCurrentTime(), parent.getNextDifficulty());
                log("Proposing new block with id="+startingValue.getId());
                printCreateBlock(startingValue);
                broadcastProtocolMessage(AlgorandMsgType.PROPOSAL, round, period, step, startingValue);
            }
            else {
                startingValue = null;
            }
        }
    }

    // Process valid messages for the current period that are in the queue
    private void processMessageQueue() {
        // This is valid because if the message is not processed, it gets added again to the end of the queue
        for(int i = 0; i < mQueue.size(); i++) {
            AlgorandMsgTask m = mQueue.remove(0);
            processMessage(m);
        }
    }

    // Add a message to a list, if it doesn't already contain a message from the same node
    // Returns true if the message is a duplicate. Returns false otherwise.
    private boolean addNoDuplicate(AlgorandMsgTask msg, ArrayList<AlgorandMsgTask> list) {
        for(AlgorandMsgTask in : list) {
            if(in.getVoteFrom().getNodeID() == msg.getVoteFrom().getNodeID()) {
                // do nothing if already received from that node
                return true; // is duplicate
            }
        }
        // if haven't received, add msg to list
        list.add(msg);
        return false; // is not duplicate
    }

    public void runStep(AlgorandIncStepTask msg) {
        switch(msg.getNextStep()) {
            case 1:
                valueProposal();
                break;
            case 2:
                try {
                    filteringStep();
                } catch (NoSuchAlgorithmException e) {
                    // Should never happen because the algorithm used does exist
                    e.printStackTrace();
                }
                break;
            case 3:
                certifyingStep();
                break;
            case 4:
                finishingStepOne();
                break;
            case 5:
                finishingStepTwo();
                break;
            default:
                break;
        }
    }

    // Advance to the next round
    private void advanceRound() {
        round += 1;
        period = 1;
        step = 1;
        cleanData();
    }

    // Clean the data from the previous round
    private void cleanData() {
        this.proposals.clear();
        this.softvotes.clear();
        this.certvotes.clear();
        this.nextvotes.clear();
        this.prevsoftvotes.clear();
        this.prevnextvotes.clear();
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
            if(vote.getBlock() != null) {
                blockIds[i] = vote.getBlock().getId();
            }
            else {
                // the vote is for the empty block
                blockIds[i] = -1;
            }
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

        // if it obtained more than the required votes, return the block according to the id
        if(mostVotes >= REQUIRED_VOTES) {
            // if the id correspond to the empty block (-1)
            if(mostVotedId == -1) {
                return new Pair<>(true, null);
            }
            // otherwise return the block with the corresponding id
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
            putTask(new AlgorandMsgTask(getSelfNode(), to, type, round, period, step, proposal, delay, getSelfNode()));
        }
        // also stores its own message, regardless of whether it is a vote or proposal
        processMessage(new AlgorandMsgTask(getSelfNode(), getSelfNode(), type, round, period, step, proposal, 0, getSelfNode()));
    }

    private void propagateMessage(AlgorandMsgTask m) {
        // propagate a received message to its neighbors
        for (Node to : getSelfNode().getRoutingTable().getNeighbors()) {
            long bandwidth = getBandwidth(getSelfNode().getRegion(), to.getRegion()); // copied from Node "sendNextBlockMessage"
            long delay = BLOCK_SIZE * 8 / (bandwidth / 1000) + 2; // copied from Node "sendNextBlockMessage"
            putTask(new AlgorandMsgTask(getSelfNode(), to, m.getType(), m.getRound(), m.getPeriod(), m.getStep(), m.getBlock(), delay, m.getVoteFrom()));
        }
    }

    // Check if node was selected by sortition to propose in the current round
    private boolean selectedBySortition(int nodeId) {
        // Since all nodes will use the round as a seed, only one node will be selected per round
        Random r = new Random(round);
        boolean res = (r.nextInt(NUM_OF_NODES) + 1) == nodeId;
        if(res) {
            printSelectedToPropose(nodeId, round);
        }
        return res;
    }

    // Check if node is in the current round's committee
    private boolean inCommitee(int nodeId) {
        // Since all nodes will use the round as a seed, they will see the same nodes being selected
        Random r = new Random(round);
        ArrayList<Integer> selected = new ArrayList(T);

        while(selected.size() < T) {
            int rNum = (r.nextInt(NUM_OF_NODES) + 1);
            if(selected.contains(rNum)) {
                continue;
            }
            if(rNum == nodeId) {
                printInCommittee(nodeId, round);
                selected.add(rNum);
                return true;
            }
            else {
                selected.add(rNum);
            }
        }

        return false;
    }

    // Increment period updating corresponding state
    private void nextPeriod() {
        log("Incrementing period.");
        period += 1; step = 1; certVoted = new Pair<>(false, null);
        prevnextvotes.clear(); prevsoftvotes.clear();
        prevnextvotes = (ArrayList<AlgorandMsgTask>) nextvotes.clone();
        prevsoftvotes = (ArrayList<AlgorandMsgTask>) softvotes.clone();
        certvotes.clear(); nextvotes.clear(); softvotes.clear();
        proposals.clear();
        processMessageQueue();
    }

    // From the received proposals, identify the leader and return the block proposed by him
    private Block findLeaderProposal() throws NoSuchAlgorithmException {
        // the leader of period p is the proposer that possesses the credentials for the round+period with smallest hash
        // for simplification, we will consider the credentials to be the round+period appended to the node's ID
        Block fromLeader = null; String minHash = ""; boolean firstIteration = true;
        String suffix = Integer.toString(round).concat(Integer.toString(period));
        for(AlgorandMsgTask proposal : proposals) {
            String credential = Integer.toString(proposal.getVoteFrom().getNodeID()) + suffix;

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

    // register a new seen block in the round
    private void registerBlock(Block b) {
        if(b == null) {
            // if it is the empty block, return
            return;
        }
        for(Block x : blocks) {
            if(x.getId() == b.getId()) {
                // if the block is already registered, return
                return;
            }
        }
        // otherwise, register the block
        blocks.add(b);
    }

    private void log(String m) {
        MyLogger.log("[Node="+getSelfNode().getNodeID()+"|Round="+round+"|Period="+period+"|Step="+step+"|Timestamp="+getCurrentTime()+"]: " + m);
    }

    /**
     * Logs the provided block to the logfile.
     *
     * @param block the block to be logged
     */
    private void printCreateBlock(Block block) {
        OUT_JSON_FILE.print("{");
        OUT_JSON_FILE.print("\"kind\":\"create-block\",");
        OUT_JSON_FILE.print("\"content\":{");
        OUT_JSON_FILE.print("\"timestamp\":" + getCurrentTime() + ",");
        OUT_JSON_FILE.print("\"node-id\":" + getSelfNode().getNodeID() + ",");
        OUT_JSON_FILE.print("\"block-id\":" + block.getId());
        OUT_JSON_FILE.print("}");
        OUT_JSON_FILE.print("},");
        OUT_JSON_FILE.flush();
    }

    /**
     * Log that node with ID = id is selected to propose in round=r
     */
    private void printSelectedToPropose(int id, int r) {
        OUT_JSON_FILE.print("{");
        OUT_JSON_FILE.print("\"kind\":\"node-proposer\",");
        OUT_JSON_FILE.print("\"content\":{");
        OUT_JSON_FILE.print("\"timestamp\":" + getCurrentTime() + ",");
        OUT_JSON_FILE.print("\"node-id\":" + id + ",");
        OUT_JSON_FILE.print("\"round\":" + r);
        OUT_JSON_FILE.print("}");
        OUT_JSON_FILE.print("},");
        OUT_JSON_FILE.flush();
    }

    /**
     * Log that node with ID = id is part of committee in round=r
     */
    private void printInCommittee(int id, int r) {
        OUT_JSON_FILE.print("{");
        OUT_JSON_FILE.print("\"kind\":\"node-committee\",");
        OUT_JSON_FILE.print("\"content\":{");
        OUT_JSON_FILE.print("\"timestamp\":" + getCurrentTime() + ",");
        OUT_JSON_FILE.print("\"node-id\":" + id + ",");
        OUT_JSON_FILE.print("\"round\":" + r);
        OUT_JSON_FILE.print("}");
        OUT_JSON_FILE.print("},");
        OUT_JSON_FILE.flush();
    }

}

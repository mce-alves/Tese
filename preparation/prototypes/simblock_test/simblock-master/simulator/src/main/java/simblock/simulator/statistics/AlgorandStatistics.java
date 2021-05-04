package simblock.simulator.statistics;

import simblock.settings.SimulationConfiguration;
import simblock.task.Task;
import simblock.task.algorand.AlgorandMsgTask;

import java.lang.reflect.Array;
import java.util.ArrayList;

import static simblock.simulator.Main.OUT_JSON_FILE;
import static simblock.simulator.Timer.getCurrentTime;

public final class AlgorandStatistics {

    private static final AlgorandStatistics INSTANCE = new AlgorandStatistics();

    private static ArrayList<Integer> nodeNumBlocks;
    private static ArrayList<Long> lastConsensusTime;
    private static ArrayList<ArrayList<Long>> nodeTimeBetweenBlocks; // consensus
    private static long totalMessagesExchanged;

    private AlgorandStatistics() {
        this.nodeNumBlocks = new ArrayList<>();
        this.lastConsensusTime = new ArrayList<>();
        this.nodeTimeBetweenBlocks = new ArrayList<>();
        this.totalMessagesExchanged = 0;
        initData();
    }

    private void initData() {
        for(int i = 0; i < SimulationConfiguration.NUM_OF_NODES; i++) {
            nodeNumBlocks.add(0);
            lastConsensusTime.add(0L);
            nodeTimeBetweenBlocks.add(new ArrayList<>());
        }
    }

    public static AlgorandStatistics getInstance() {
        return INSTANCE;
    }

    public static void consensusReached(int nodeId, long timestamp, int height) {
        nodeNumBlocks.set(nodeId-1, height); // update node's current height
        long time = timestamp - lastConsensusTime.get(nodeId-1); // calculate time taken to reach consensus since previous block
        lastConsensusTime.set(nodeId-1, timestamp); // update last consensus time
        nodeTimeBetweenBlocks.get(nodeId-1).add(time); // store time to reach consensus since previous block
    }

    public static void gatherStatistics(Task t) {
        if(t instanceof AlgorandMsgTask) {
            totalMessagesExchanged++; // increase number of messages exchanged
        }
    }

    public static void printStatistics() {
        ///// Calculate average time to reach consensus
        ArrayList<Long> avgTimeToConsensusPerNode = new ArrayList<>();
        for(ArrayList<Long> tbb : nodeTimeBetweenBlocks) {
            long sum = 0;
            for(Long l : tbb) {
                sum += l;
            }
            avgTimeToConsensusPerNode.add(sum/tbb.size());
        }
        long sum = 0;
        for(Long l : avgTimeToConsensusPerNode) {
            sum += l;
        }
        long avgConsensusTime = sum/avgTimeToConsensusPerNode.size();
        ///// Calculate max height reached during simulation
        int maxHeight = 0;
        for(int h : nodeNumBlocks) {
            if(h > maxHeight) {
                maxHeight = h;
            }
        }
        ///// Print statistics to JSON file
        OUT_JSON_FILE.print("{");
        OUT_JSON_FILE.print("\"kind\":\"statistics\",");
        OUT_JSON_FILE.print("\"content\":{");
        OUT_JSON_FILE.print("\"avgConsensusTime\":" + avgConsensusTime + ",");
        OUT_JSON_FILE.print("\"maxChainHeight\":" + maxHeight + ",");
        OUT_JSON_FILE.print("\"totalMessagesExchanged\":" + totalMessagesExchanged);
        OUT_JSON_FILE.print("}");
        OUT_JSON_FILE.print("},");
        OUT_JSON_FILE.flush();
    }

}

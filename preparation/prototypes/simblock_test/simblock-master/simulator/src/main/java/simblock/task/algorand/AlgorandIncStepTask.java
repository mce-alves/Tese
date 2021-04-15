package simblock.task.algorand;

import simblock.node.Node;
import simblock.task.AbstractMessageTask;

public class AlgorandIncStepTask extends AbstractMessageTask {

    private long interval;
    private int nextStep;


    /**
     * Instantiates a new Abstract message task.
     *
     * @param from the creating entity
     * @param interval the amount of time before the task should be executed
     */
    public AlgorandIncStepTask(Node from, long interval, int nextStep) {
        super(from, from);
        this.interval = interval;
        this.nextStep = nextStep;
    }

    public int getNextStep() {
        return this.nextStep;
    }


    @Override
    public long getInterval() {
        return this.interval;
    }
}

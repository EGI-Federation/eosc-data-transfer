package eosc.eu;


/***
 * Class to wrap a boolean value, used as accumulator for Multi streams
 */
public class BooleanAccumulator {
    private boolean state;

    /**
     * Constructor
     */
    public BooleanAccumulator() {
        this.state = false;
    }

    /***
     * OR with current result
     * @param b is operand to OR with current state
     */
    public void accumulateAny(boolean b) {
        this.state = this.state || b;
    }

    /***
     * AND with current result
     * @param b is operand to AND with current state
     */
    public void accumulateAll(boolean b) {
        this.state = this.state && b;
    }

    /***
     * Get the current result
     * @return aggregated result
     */
    public boolean get() { return this.state; }
}

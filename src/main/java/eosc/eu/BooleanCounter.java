package eosc.eu;


/***
 * Class to wrap a counter, used as accumulator for Multi streams
 */
public class BooleanCounter {
    private long count;

    public BooleanCounter() {
        this.count = 0;
    }

    /***
     * Count successes
     * @param b is outcome of the counted operation
     */
    public void accumulateSuccess(boolean b) {
        if(b)
            this.count++;
    }

    /***
     * Count failures
     * @param b is outcome of the counted operation
     */
    public void accumulateFailure(boolean b) {
        if(!b)
            this.count++;
    }

    /***
     * Get the current counter
     * @return counted operations
     */
    public long get() { return this.count; }
}

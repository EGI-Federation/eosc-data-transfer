package eosc.eu;


/***
 * Class to wrap a counter, used as accumulator for Multi streams
 */
public class BooleanCounter {
    private long count;

    public BooleanCounter() {
        this.count = 0;
    }

    public void accumulateSuccess(boolean b) {
        if(b)
            this.count++;
    }

    public void accumulateFailure(boolean b) {
        if(!b)
            this.count++;
    }

    public long get() { return this.count; }
}

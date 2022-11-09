package eosc.eu;


/***
 * Class to wrap a boolean value, used as accumulator for Multi streams
 */
public class BooleanAccumulator {
    private boolean state;

    public BooleanAccumulator() {
        this.state = false;
    }

    public void accumulateAny(boolean b) {
        this.state = this.state || b;
    }

    public void accumulateAll(boolean b) {
        this.state = this.state && b;
    }

    public boolean get() { return this.state; }
}

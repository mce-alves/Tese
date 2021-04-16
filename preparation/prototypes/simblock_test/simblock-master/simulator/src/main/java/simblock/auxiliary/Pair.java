package simblock.auxiliary;

// https://www.techiedelight.com/implement-pair-class-java/
public class Pair<U,V> {

    public final U first;
    public final V second;

    public Pair(U first, V second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(o == null || getClass() != o.getClass()) {
            return false;
        }
        Pair<?,?> pair = (Pair<?, ?>) o;
        if(!first.equals(pair.first)) {
            return false;
        }
        return second.equals(pair.second);
    }

    @Override
    public int hashCode()
    {
        return 31 * first.hashCode() + second.hashCode();
    }

    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }

}

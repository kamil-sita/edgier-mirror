package pl.sita.edgymirror;

import java.util.Objects;

public class Pair<X, U> {

    private final X x;
    private final U u;

    public Pair(X x, U u) {
        this.x = x;
        this.u = u;
    }

    public X getX() {
        return x;
    }

    public U getY() {
        return u;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(x, pair.x) && Objects.equals(u, pair.u);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, u);
    }

    @Override
    public String toString() {
        return "Pair{" +
            "x=" + x +
            ", y=" + u +
            '}';
    }
}

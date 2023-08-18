package bali.java.sample.naming;

public interface Nameable {

    default String qualified() {
        return getClass().getName();
    }

    default String simple() {
        return getClass().getSimpleName();
    }
}

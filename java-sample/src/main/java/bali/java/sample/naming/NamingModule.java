package bali.java.sample.naming;

import bali.Module;

@Module
public interface NamingModule {

    @SuppressWarnings("unused")
    interface WhatsMyName<Complication> {

        default String qualified() {
            return getClass().getName();
        }

        default String simple() {
            return getClass().getSimpleName();
        }
    }

    WhatsMyName<String> name1();

    WhatsMyName<Integer> name2();
}

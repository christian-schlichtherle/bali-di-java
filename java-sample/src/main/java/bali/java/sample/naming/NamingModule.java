package bali.java.sample.naming;

import bali.Module;

@Module
public interface NamingModule extends Nameable {

    WhatsMyName<String> name1();

    WhatsMyName<Integer> name2();
}

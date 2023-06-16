package bali.java.sample.cache;

import bali.Cache;
import bali.Module;

import java.util.Date;

import static bali.CachingStrategy.*;

@Cache(NOT_THREAD_SAFE)
public interface SuperModule {

    @Cache(DISABLED)
    Date superDisabled();

    Date superNotThreadSafe();

    @Cache
    Date superThreadSafe();

    @Cache(THREAD_LOCAL)
    Date superThreadLocal();
}

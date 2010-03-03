package org.esa.beam.dataio.chris;

import org.esa.beam.framework.datamodel.PointingFactoryRegistry;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class ChrisPointingFactoryLoadedAsServiceTest {

    @Test
    public void pointingFactoryIsRegistered() {
        final PointingFactoryRegistry registry = PointingFactoryRegistry.getInstance();

        // raw products
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M1_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M2_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M3_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M4_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M5_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M20_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M30_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M3A_GC").getClass());

        // noise-corrected products
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M1_NR_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M2_NR_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M3_NR_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M4_NR_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M5_NR_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M20_NR_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M30_NR_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M3A_NR_GC").getClass());

        // atmosphere-corrected products
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M1_NR_AC_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M2_NR_AC_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M3_NR_AC_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M4_NR_AC_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M5_NR_AC_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M20_NR_AC_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M30_NR_AC_GC").getClass());
        assertSame(ChrisPointingFactory.class, registry.getPointingFactory("CHRIS_M3A_NR_AC_GC").getClass());
    }

}

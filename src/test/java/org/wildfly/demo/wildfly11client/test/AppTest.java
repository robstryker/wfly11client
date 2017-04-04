package org.wildfly.demo.wildfly11client.test;

import org.junit.Test;
import org.wildfly.demo.wildfly11client.WildFlyClient;

/**
 * Unit test for simple App.
 */
public class AppTest {
    /**
     * Rigourous Test :-)
     */
    @Test
    public void testIncrementalNestedChange() throws Exception {
      new WildFlyClient().runIncrementalTest();
    }

    @Test
    public void testMultipleExplodedFullDeploy() throws Exception {
        new WildFlyClient().runExplodedTest();
    }

}

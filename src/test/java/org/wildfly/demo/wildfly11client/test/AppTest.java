package org.wildfly.demo.wildfly11client.test;

import org.junit.Test;
import org.junit.Assert;
import org.wildfly.demo.wildfly11client.WildFlyClient;

/**
 * Unit test for simple App.
 */
public class AppTest {
    /**
     * Rigourous Test :-)
     */
    @Test
    public void testApp() {
       try {
          WildFlyClient.main(new String[] {});
       } catch(Exception e) {
          e.printStackTrace();
          Assert.fail(e.getMessage());
       }
    }
}

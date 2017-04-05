package org.wildfly.demo.wildfly11client;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;
import org.jboss.as.controller.client.helpers.standalone.ServerUpdateActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerUpdateActionResult.Result;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.auth.callback.CallbackUtil;
import org.wildfly.security.auth.callback.CredentialCallback;
import org.wildfly.security.auth.callback.OptionalNameCallback;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.DigestPassword;
import org.wildfly.security.util.CodePointIterator;

public class WildFlyClient {

    public static final String USER = "test4";
    public static final String PASS = "testpassword1";
    public static final String DATA_PREFIX = System.getProperty("basedir", "/home/ehsavoie/dev/wfly11client") + "/data/";

    public static void main(String[] args) throws Exception {
        new WildFlyClient().runIncrementalTest();
    }

    ModelControllerClient client;
    ServerDeploymentManager manager;

    public void runExplodedTest() throws Exception {
        try {
            this.client = ModelControllerClient.Factory.create("localhost", 9990, new TestWF11CallbackHandler(), null,
                    30000);
            this.manager = ServerDeploymentManager.Factory.create(client);

            undeploy("out.war", true);
            DeploymentOperationResult result = deploy("out.war",
                    new File(DATA_PREFIX + "out_with_jar.war"),
                    new String[] { "out.war", "out.war/WEB-INF/lib/UtilOne.jar" }, true);
            waitFor(result, "Some task");

            System.out.println(result.getStatus().getResult());
            if (result.getStatus().getResult() != ServerUpdateActionResult.Result.EXECUTED) {
                System.out.println("Failed to execute:" + result.getStatus().getResult());
                throw new Exception("Failed");
            }
            String contents = waitForRespose("out/TigerServ", "localhost", 8080);
            System.out.println(contents);
            if (!contents.startsWith("Served jar:")) {
                System.out.println("Failed expected output prefix");
                throw new Exception("Failed");
            }
        } finally {
            client.close();
        }
    }


    public void runIncrementalTest() throws Exception {
        try {
            this.client = ModelControllerClient.Factory.create("localhost", 9990, new TestWF11CallbackHandler(), null,
                    30000);
            this.manager = ServerDeploymentManager.Factory.create(client);

            undeploy("out.war", true);

            DeploymentOperationResult result = deploy("out.war",
                    new File(DATA_PREFIX + "out_initial.war"),
                    new String[] { "out.war" }, true);
            waitFor(result, "Some task");
            System.out.println(result.getStatus().getResult());
            if (result.getStatus().getResult() != ServerUpdateActionResult.Result.EXECUTED) {
                System.out.println("Failed to execute:" + result.getStatus().getResult());
                throw new Exception("Failed");
            }
            String contents = waitForRespose("out/TigerServ", "localhost", 8080);
            System.out.println(contents);
            if (!contents.startsWith("Served at:")) {
                System.out.println("Failed expected output");
                throw new Exception("Failed");
            }

            IncrementalManagementModel m = new IncrementalManagementModel();
            Map<String, String> changedContent = new HashMap<String, String>();
            List<String> removedContent = new ArrayList<String>();
            changedContent.put("WEB-INF/classes/my/pak/TigerServ.class",
                    DATA_PREFIX + "TigerServ_change1.class");
            m.put("out.war", changedContent, removedContent);
            incrementalPublish("out.war", m, true);

            contents = waitForRespose("out/TigerServ", "localhost", 8080);
            System.out.println(contents);
            if (!contents.startsWith("Served with:")) {
                System.out.println("Failed expected output");
                throw new Exception("Failed");
            }

            // Do a full publish with a war that has a nested jar
            undeploy("out.war", true);

            result = deploy("out.war",
                    new File(DATA_PREFIX + "out_with_jar.war"),
                    new String[] { "out.war" }, true);
            waitFor(result, "Some task");
            System.out.println(result.getStatus().getResult());
            if (result.getStatus().getResult() != ServerUpdateActionResult.Result.EXECUTED) {
                System.out.println("Failed to execute:" + result.getStatus().getResult());
                throw new Exception("Failed");
            }
            contents = waitForRespose("out/TigerServ", "localhost", 8080);
            System.out.println(contents);
            if (!contents.startsWith("Served jar:")) {
                System.out.println("Failed expected output prefix");
                throw new Exception("Failed");
            }

            // web should return something like:
            // Served jar:1491340851960:/DWe87rbb:Util:0

            String[] split = contents.split(":");
            if (split.length != 5) {
                System.out.println("Failed expected segment count");
                throw new Exception("Failed");
            }
            if (!split[3].equals("Util")) {
                System.out.println("Fourth segment should be 'Util'");
                throw new Exception("Failed");
            }

            //Explode the jar
            ServerDeploymentPlanResult planResult = explode("out.war" , "WEB-INF/lib/UtilOne.jar");
            System.out.println(planResult);
            // incrementally update the class file inside the jar inside the war
            m = new IncrementalManagementModel();
            changedContent = new HashMap<String, String>();
            removedContent = new ArrayList<String>();
            changedContent.put("util/pak/UtilModel.class",
                    DATA_PREFIX + "UtilModel_Change1.class");
            m.put("out.war/WEB-INF/lib/UtilOne.jar", changedContent, removedContent);
            ServerDeploymentActionResult r = incrementalPublish("out.war/WEB-INF/lib/UtilOne.jar", m, true);
            System.out.println(r.getResult());
            if (r.getResult() == Result.NOT_EXECUTED) {
                System.out.println("Failed incremental change to file inside util jar inside war");
                throw new Exception("Failed");
            }

            contents = waitForRespose("out/TigerServ", "localhost", 8080);
            System.out.println(contents);

            // web should return something like below.  Note the "Util" segment has changed to "Util6"
            // Served jar:1491340851960:/DWe87rbb:Util6:0
            split = contents.split(":");
            if (split.length != 5) {
                System.out.println("Failed expected segment count");
                throw new Exception("Failed");
            }
            if (!split[3].equals("Util6")) {
                System.out.println("Fourth segment should be 'Util6'");
                throw new Exception("Failed");
            }
        } finally {
            client.close();
        }
    }

    private void runStandardTest() throws Exception {
        try {
            this.client = ModelControllerClient.Factory.create("localhost", 9990, new TestWF11CallbackHandler(), null,
                    30000);
            this.manager = ServerDeploymentManager.Factory.create(client);
            String request = "{\"operation\" : \"read-resource\", \"address\" : [{ \"subsystem\" : \"deployment-scanner\" },{ \"scanner\" : \"*\" }]}";
            ModelNode node = ModelNode.fromJSONString(request);
            execute(node);
        } catch (Exception e) {
            System.out.println("Fail");
            e.printStackTrace();
        } finally {
            client.close();
        }
    }

    public ServerDeploymentPlanResult explode(String deploymentName, String path) throws Exception {
        DeploymentPlanBuilder b = manager.newDeploymentPlan();
        b.undeploy(deploymentName);
        b.explodeDeploymentContent(deploymentName, path);
        b.deploy(deploymentName);
        DeploymentPlan plan = b.build();
        Future<ServerDeploymentPlanResult> future = manager.execute(plan);
        return future.get(5000, TimeUnit.MILLISECONDS);
    }

    public ServerDeploymentActionResult incrementalPublish(String deploymentName, IncrementalManagementModel model,
            boolean redeploy) throws Exception {

        DeploymentPlanBuilder b = manager.newDeploymentPlan();
        try {
            String[] deployments = model.keys();
            for (int i = 0; i < deployments.length; i++) {
                Map<String, String> changed = model.getChanged(deployments[i]);
                List<String> removed = model.getRemoved(deployments[i]);
                b = addChanges(deployments[i], changed, removed, b);
            }

            if (redeploy)
                b = b.redeploy(deploymentName);

            DeploymentAction action = b.getLastAction();
            DeploymentPlan plan = b.build();
            Future<ServerDeploymentPlanResult> future = manager.execute(plan);
            DeploymentOperationResult res = new DeploymentOperationResult(action, future, 5000, TimeUnit.MILLISECONDS);
            return waitFor(res, "Incremental Deployment");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw e;
        }
    }

    private DeploymentPlanBuilder addChanges(String deploymentName, Map<String, String> changedContent,
            List<String> removedContent, DeploymentPlanBuilder bd) throws IOException {

        Map<String, InputStream> addStreams = new HashMap<>();
        Iterator<String> changedIt = changedContent.keySet().iterator();
        ArrayList<String> failedChanges = new ArrayList<String>();
        while (changedIt.hasNext()) {
            String k = changedIt.next();
            String v = changedContent.get(k);
            try {
                addStreams.put(k, new FileInputStream(new File(v)));
            } catch (FileNotFoundException e) {
                failedChanges.add(k);
            }
        }

        return bd.addContentToDeployment(deploymentName, addStreams).removeContenFromDeployment(deploymentName,
                removedContent);
    }

    private ServerDeploymentActionResult waitFor(DeploymentOperationResult result, String task) throws Exception {
        while (!result.isDone()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                // Ignore
            }
        }
        return result.getStatus();
    }

    public static boolean isSuccess(ModelNode operationResult) {
        if (operationResult != null) {
            ModelNode outcome = operationResult.get("outcome");
            return outcome != null && outcome.asString().equals("success");
        }
        return false;
    }

    /* package */ ModelNode execute(ModelNode node) throws Exception {
        try {
            ModelNode response = client.execute(node);
            boolean b = isSuccess(response);
            System.out.println("Success? " + b);
            if (!isSuccess(response)) {
                throw new Exception();
            }
            return response.get("result");
        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    public DeploymentOperationResult deploy(String name, File file, String[] explodedPaths, boolean add)
            throws Exception {
        try {
            DeploymentPlanBuilder b = manager.newDeploymentPlan();
            if (add)
                b = b.add(name, file);
            for (int i = 0; i < explodedPaths.length; i++) {
                b = b.explodeDeployment(explodedPaths[i]);
            }
            return execute(b.deploy(name));
        } catch (IOException e) {
            throw e;
        }
    }

    private DeploymentOperationResult execute(DeploymentPlanBuilder builder) throws Exception {
        try {
            DeploymentAction action = builder.getLastAction();
            Future<ServerDeploymentPlanResult> planResult = manager.execute(builder.build());
            return new DeploymentOperationResult(action, planResult);
        } catch (Exception e) {
            throw e;
        }
    }

    protected static class TestCallbackHandler implements CallbackHandler {
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            if (callbacks.length == 1 && callbacks[0] instanceof NameCallback) {
                ((NameCallback) callbacks[0]).setName("anonymous JBossTools user");
                return;
            }

            NameCallback name = null;
            PasswordCallback pass = null;
            for (Callback current : callbacks) {
                if (current instanceof RealmCallback) {
                    RealmCallback rcb = (RealmCallback) current;
                    String defaultText = rcb.getDefaultText();
                    rcb.setText(defaultText); // For now just use the realm
                                                // suggested.
                }
                if (current instanceof NameCallback) {
                    name = (NameCallback) current;
                    name.setName(USER);
                } else if (current instanceof PasswordCallback) {
                    pass = (PasswordCallback) current;
                    pass.setPassword(PASS.toCharArray());
                }
            }
        }
    }

    protected static class TestWF11CallbackHandler implements CallbackHandler {
        private String realm;

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            if (callbacks.length == 2 && callbacks[0] instanceof OptionalNameCallback) {
                return;
            }

            NameCallback name = null;
            PasswordCallback pass = null;
            for (Callback current : callbacks) {
                if (current instanceof RealmCallback) {
                    RealmCallback rcb = (RealmCallback) current;
                    realm = rcb.getPrompt();
                    String defaultText = rcb.getDefaultText();
                    rcb.setText(defaultText); // For now just use the realm
                                                // suggested.
                }
                if (current instanceof NameCallback) {
                    name = (NameCallback) current;
                    name.setName(USER);
                } else if (current instanceof PasswordCallback) {
                    pass = (PasswordCallback) current;
                    pass.setPassword(PASS.toCharArray());
                } else if (current instanceof CredentialCallback) {
                    String digest = null;
                    CredentialCallback cred = (CredentialCallback) current;
                    if (digest == null && cred.isCredentialTypeSupported(PasswordCredential.class,
                            ClearPassword.ALGORITHM_CLEAR)) {
                        cred.setCredential(new PasswordCredential(
                                ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, PASS.toCharArray())));
                    } else if (digest != null && cred.isCredentialTypeSupported(PasswordCredential.class,
                            DigestPassword.ALGORITHM_DIGEST_MD5)) {
                        // We don't support an interactive use of this callback
                        // so it must have been set in advance.
                        final byte[] bytes = CodePointIterator.ofString(digest).hexDecode().drain();
                        cred.setCredential(new PasswordCredential(
                                DigestPassword.createRaw(DigestPassword.ALGORITHM_DIGEST_MD5, USER, realm, bytes)));
                    } else {
                        CallbackUtil.unsupported(current);
                    }
                }
            }
        }
    }

    public static String waitForRespose(String name, String host, int port) throws IOException {
        HttpURLConnection response1 = waitForResponseCode(200, name, host, port);
        response1.disconnect();
        String result = getResponse(name, host, port);
        return result;
    }

    public static HttpURLConnection waitForResponseCode(int code, String name, String host, int port)
            throws IOException {
        URL url = new URL("http://" + host + ":" + port + "/" + name);
        long until = System.currentTimeMillis() + (10 * 1024);
        int resetCount = 0;
        while (System.currentTimeMillis() < until) {
            HttpURLConnection connection = connect(url);
            try {
                if (connection.getResponseCode() == code) {
                    return connection;
                }
            } catch (FileNotFoundException e) {
                if (code == 404) {
                    return connection;
                }
                throw e;
            } catch (SocketException se) {
                resetCount++;
                if (resetCount >= 10)
                    throw se;
            } finally {
                connection.disconnect();
            }
        }
        throw new RuntimeException("wait on url " + url + " for response code " + code + " timed out.");
    }

    public static String getResponse(String name, String host, int port) throws IOException {
        URL url = new URL("http://" + host + ":" + port + "/" + name);
        HttpURLConnection connection = connect(url);
        String s = toString(new BufferedInputStream(connection.getInputStream()));
        connection.disconnect();
        return s;
    }

    public static String toString(InputStream in) throws IOException {
        StringWriter writer = new StringWriter();
        for (int data = -1; ((data = in.read()) != -1);) {
            writer.write(data);
        }
        return writer.toString();
    }

    private static HttpURLConnection connect(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setAllowUserInteraction(false);
        connection.setConnectTimeout(10 * 1024);
        connection.setInstanceFollowRedirects(true);
        connection.setDoOutput(false);
        return connection;
    }

    public DeploymentOperationResult undeploy(String name, boolean removeFile) throws Exception {
        try {
            DeploymentPlanBuilder builder = manager.newDeploymentPlan();
            if (removeFile)
                builder = builder.undeploy(name).andRemoveUndeployed();
            else
                builder = builder.undeploy(name);
            return new DeploymentOperationResult(builder.getLastAction(), manager.execute(builder.build()));
        } catch (Exception e) {
            throw e;
        }
    }

}

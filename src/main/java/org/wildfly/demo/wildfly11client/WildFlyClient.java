package org.wildfly.demo.wildfly11client;


import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.auth.callback.CallbackUtil;
import org.wildfly.security.auth.callback.CredentialCallback;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.DigestPassword;
import org.wildfly.security.util.CodePointIterator;

public class WildFlyClient {

    public static void main(String[] args) throws Exception {
        new WildFlyClient().run();

    }

    ModelControllerClient client;
    ServerDeploymentManager manager;

    private void run() throws Exception {
        try {
            this.client = ModelControllerClient.Factory.create(
                    "localhost", 9990,
                    new TestCallbackHandler(), null, 30000);
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

    public static boolean isSuccess(ModelNode operationResult) {
        if (operationResult != null) {
            ModelNode outcome = operationResult.get("outcome");
            return outcome != null && outcome.asString().equals("success");
        }
        return false;
    }

    /*package*/ ModelNode execute(ModelNode node) throws Exception {
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

    protected static class TestCallbackHandler implements CallbackHandler {
        private String digest;
        private String password = "passw0rd!";
        private String username = "admin";
        private String realm;

        public void handle(Callback[] callbacks) throws IOException,
                UnsupportedCallbackException {
            if (callbacks.length == 1 && callbacks[0] instanceof NameCallback) {
                ((NameCallback) callbacks[0]).setName("anonymous JBossTools user");
                return;
            }

            for (Callback current : callbacks) {
                if (current instanceof RealmCallback) {
                    RealmCallback rcb = (RealmCallback) current;
                    realm = rcb.getPrompt();
                    String defaultText = rcb.getDefaultText();
                    rcb.setText(defaultText); // For now just use the realm suggested.
                }
                if (current instanceof NameCallback) {
                    NameCallback name = (NameCallback) current;
                    name.setName(username);
                } else if (current instanceof PasswordCallback) {
                    PasswordCallback pass = (PasswordCallback) current;
                    pass.setPassword(password.toCharArray());
                } else if (current instanceof CredentialCallback) {
                    CredentialCallback cred = (CredentialCallback) current;
                    if (digest == null && cred.isCredentialTypeSupported(PasswordCredential.class, ClearPassword.ALGORITHM_CLEAR)) {
                        cred.setCredential(new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, password.toCharArray())));
                    } else if (digest != null && cred.isCredentialTypeSupported(PasswordCredential.class, DigestPassword.ALGORITHM_DIGEST_MD5)) {
                        // We don't support an interactive use of this callback so it must have been set in advance.
                        final byte[] bytes = CodePointIterator.ofString(digest).hexDecode().drain();
                        cred.setCredential(new PasswordCredential(DigestPassword.createRaw(DigestPassword.ALGORITHM_DIGEST_MD5, username, realm, bytes)));
                    } else {
                        CallbackUtil.unsupported(current);
                    }
                }
            }
        }
    }
}

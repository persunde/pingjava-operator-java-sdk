package ch.cern.pingjava;

import com.github.containersolutions.operator.Operator;
import com.github.containersolutions.operator.processing.retry.GenericRetry;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;

import java.io.IOException;

public class Runner {

    private static final Logger log = LoggerFactory.getLogger(Runner.class);

    public static void main(String[] args) throws IOException {
        log.info("Runner PingJava Operator starting!");

        Config config = new ConfigBuilder().withNamespace(null).build();
        KubernetesClient client = new DefaultKubernetesClient(config);
        Operator operator = new Operator(client);
        GenericRetry retry = GenericRetry.every10second10TimesRetry(); /* On Failure: retries every 10 second, and max 10 times, you can customize this yourself if you want */

        CustomServiceController controller = new CustomServiceController(client);
        operator.registerControllerForAllNamespaces(controller, retry);

        /*
        * New thread that queries the Webservers deployed to the cluster in a 5 sec interval.
        * Calculates the latency and scales up/down or keep as is, depending on the latency.
        * NOTE: Probably not thread safe, but it is ok for this Proof-of-Concept
        *
        * This will/can be replaced with a new upcoming feature in the Java-Operatpr-SDK
        */
        Runnable runnable = () -> {
            try {
                Thread.sleep(20 * 1000);
                while (true) {
                    Thread.sleep(5 * 1000);
                    controller.checkStatus();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
        Thread t = new Thread(runnable);
        t.start();

        new FtBasic(
                new TkFork(new FkRegex("/health", "ALL GOOD!")), 8080
        ).start(Exit.NEVER);
    }
}

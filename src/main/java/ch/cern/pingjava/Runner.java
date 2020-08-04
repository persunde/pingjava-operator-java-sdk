package ch.cern.pingjava;

import com.github.containersolutions.operator.Operator;
import com.github.containersolutions.operator.processing.retry.GenericRetry;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;

import java.io.IOException;

public class Runner {

    public static void main(String[] args) throws IOException {
        KubernetesClient client = new DefaultKubernetesClient();
        Operator operator = new Operator(client);
        GenericRetry retry = GenericRetry.every10second10TimesRetry(); /* retries every 10 second, and max 10 times, you can customize this yourself if you want */

        operator.registerController(new CustomServiceController(client), retry);

        new FtBasic(
                new TkFork(new FkRegex("/health", "ALL GOOD!")), 8080
        ).start(Exit.NEVER);
    }
}
